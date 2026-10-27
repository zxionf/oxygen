// shio 的 JNI 薄层。
//
// 职责边界（见 .design/changes/20261023-kernel-integration-demo/design.md）：
// - Kotlin 侧只看到不透明的 jlong 句柄，不接触 shio_kernel* 裸指针。
// - 句柄表 + 每句柄互斥量保证 close/destroy 与在途调用不竞态。
// - 只做字符串/字节转换、错误码映射、输出缓冲区复制与释放，不复制内核逻辑。
// - 口令进入内核后立即清零本地副本。

#include <jni.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "shio.h"

namespace {

constexpr char kExceptionClass[] = "top/zxionf/shio/kernel/ShioException";
constexpr char kExceptionSignature[] = "(ILjava/lang/String;)V";

struct Handle {
    shio_kernel* kernel = nullptr;
    std::mutex mutex;
    bool destroyed = false;
};

std::mutex g_table_mutex;
std::unordered_map<jlong, std::shared_ptr<Handle>> g_handles;
std::atomic<jlong> g_next_id{1};

jclass g_exception_class = nullptr;
jmethodID g_exception_ctor = nullptr;

// 不会因为编译器优化而消失的内存清零。
void secure_zero(void* data, std::size_t size) {
    auto* bytes = static_cast<volatile unsigned char*>(data);
    while (size-- > 0) {
        *bytes++ = 0;
    }
}

struct ByteEraser {
    std::vector<std::uint8_t>& bytes;
    ~ByteEraser() {
        if (!bytes.empty()) {
            secure_zero(bytes.data(), bytes.size());
        }
    }
};

const char* default_message(jint code) {
    switch (code) {
        case SHIO_INVALID_ARGUMENT:
            return "invalid argument";
        case SHIO_INVALID_NAME:
            return "invalid namespace or collection name";
        case SHIO_INVALID_KEY:
            return "invalid password or corrupted key material";
        case SHIO_NOT_FOUND:
            return "record or database not found";
        case SHIO_CLOSED:
            return "database handle is closed";
        case SHIO_READ_ONLY:
            return "handle is read-only";
        case SHIO_BUSY:
            return "key rotation or upgrade is in progress";
        case SHIO_LOCKED:
            return "database is locked by another process";
        case SHIO_VALUE_TOO_LARGE:
            return "record value is too large";
        case SHIO_CORRUPTED:
            return "data is corrupted or failed authentication";
        case SHIO_UNSUPPORTED_FORMAT:
            return "unsupported format version";
        case SHIO_SERIALIZE:
            return "JSON serialization failed";
        case SHIO_DESERIALIZE:
            return "JSON deserialization failed";
        case SHIO_IO:
            return "filesystem error";
        case SHIO_DATABASE:
            return "storage engine error";
        case SHIO_INTERNAL:
            return "internal error";
        case SHIO_SYNC_DISABLED:
            return "sync is not enabled";
        case SHIO_SYNC_ENABLED:
            return "physical delete is disabled in sync mode";
        case SHIO_SYNC_RESYNC_REQUIRED:
            return "sync cursor expired, resync required";
        case SHIO_ROTATION_REQUIRED:
            return "record references a missing key";
        case SHIO_PAIRING_EXPIRED:
            return "pairing package expired";
        case SHIO_PAIRING_INVALID:
            return "pairing package is invalid";
        default:
            return "unknown shio error";
    }
}

std::string read_last_error() {
    const std::size_t needed = shio_last_error(nullptr, nullptr, 0);
    if (needed == 0) {
        return {};
    }
    std::vector<char> buffer(needed);
    shio_last_error(nullptr, buffer.data(), buffer.size());
    buffer[needed - 1] = '\0';
    return std::string(buffer.data());
}

void throw_shio(JNIEnv* env, jint code, const std::string& detail) {
    if (env->ExceptionCheck()) {
        return;
    }
    std::string message = detail.empty() ? default_message(code) : detail;
    if (g_exception_class != nullptr && g_exception_ctor != nullptr) {
        jstring jmessage = env->NewStringUTF(message.c_str());
        if (jmessage != nullptr) {
            jobject exception = env->NewObject(g_exception_class, g_exception_ctor, code, jmessage);
            if (exception != nullptr) {
                env->Throw(static_cast<jthrowable>(exception));
                env->DeleteLocalRef(exception);
                env->DeleteLocalRef(jmessage);
                return;
            }
        }
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
    // ShioException 不可用时退化为 RuntimeException，错误码写进消息。
    const std::string fallback =
        "shio error " + std::to_string(code) + ": " + message;
    jclass runtime = env->FindClass("java/lang/RuntimeException");
    if (runtime != nullptr) {
        env->ThrowNew(runtime, fallback.c_str());
        env->DeleteLocalRef(runtime);
    }
}

void throw_kernel_error(JNIEnv* env, jint code) {
    throw_shio(env, code, read_last_error());
}

void throw_closed(JNIEnv* env) {
    throw_shio(env, SHIO_CLOSED, "database handle is destroyed");
}

std::shared_ptr<Handle> acquire(jlong id) {
    std::lock_guard<std::mutex> lock(g_table_mutex);
    auto it = g_handles.find(id);
    if (it == g_handles.end()) {
        return nullptr;
    }
    return it->second;
}

bool utf16_to_utf8(const jchar* chars, jsize length, std::string* out) {
    out->clear();
    out->reserve(static_cast<std::size_t>(length));
    for (jsize i = 0; i < length; ++i) {
        const jchar unit = chars[i];
        std::uint32_t code_point;
        if (unit == 0) {
            // C ABI 的字符串以 NUL 结尾，内嵌 NUL 无法表达。
            return false;
        } else if (unit < 0x80) {
            out->push_back(static_cast<char>(unit));
            continue;
        } else if (unit < 0x800) {
            out->push_back(static_cast<char>(0xC0 | (unit >> 6)));
            out->push_back(static_cast<char>(0x80 | (unit & 0x3F)));
            continue;
        } else if (unit >= 0xD800 && unit <= 0xDBFF) {
            if (i + 1 >= length) {
                return false;
            }
            const jchar low = chars[i + 1];
            if (low < 0xDC00 || low > 0xDFFF) {
                return false;
            }
            code_point = 0x10000u + ((static_cast<std::uint32_t>(unit - 0xD800) << 10) |
                                     static_cast<std::uint32_t>(low - 0xDC00));
            ++i;
            out->push_back(static_cast<char>(0xF0 | (code_point >> 18)));
            out->push_back(static_cast<char>(0x80 | ((code_point >> 12) & 0x3F)));
            out->push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3F)));
            out->push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
            continue;
        } else if (unit >= 0xDC00 && unit <= 0xDFFF) {
            return false;
        } else {
            out->push_back(static_cast<char>(0xE0 | (unit >> 12)));
            out->push_back(static_cast<char>(0x80 | ((unit >> 6) & 0x3F)));
            out->push_back(static_cast<char>(0x80 | (unit & 0x3F)));
            continue;
        }
    }
    return true;
}

bool get_string(JNIEnv* env, jstring value, std::string* out) {
    if (value == nullptr) {
        return false;
    }
    const jsize length = env->GetStringLength(value);
    if (length < 0) {
        return false;
    }
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) {
        return false;
    }
    const bool ok = utf16_to_utf8(chars, length, out);
    env->ReleaseStringChars(value, chars);
    return ok;
}

// 返回 false 表示有 JNI 异常待处理（例如 OOM）。
bool get_bytes(JNIEnv* env, jbyteArray array, std::vector<std::uint8_t>* out) {
    out->clear();
    const jsize length = env->GetArrayLength(array);
    if (length < 0) {
        return false;
    }
    out->resize(static_cast<std::size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(array, 0, length,
                                reinterpret_cast<jbyte*>(out->data()));
    }
    return !env->ExceptionCheck();
}

const std::uint8_t* data_or_empty(const std::vector<std::uint8_t>& bytes) {
    static const std::uint8_t kEmpty = 0;
    return bytes.empty() ? &kEmpty : bytes.data();
}

jbyteArray copy_and_free(JNIEnv* env, std::uint8_t* data, std::size_t length) {
    if (data == nullptr) {
        return nullptr;
    }
    if (length > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) {
        shio_free(data, length);
        throw_shio(env, SHIO_INTERNAL, "output is too large for JNI");
        return nullptr;
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(length));
    if (result != nullptr && length > 0) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(length),
                                reinterpret_cast<const jbyte*>(data));
    }
    // 不论拷贝是否成功，内核缓冲区都必须归还。
    shio_free(data, length);
    return result;
}

jbyteArray bytes_to_array(JNIEnv* env, const std::uint8_t* data, std::size_t length) {
    if (length > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) {
        throw_shio(env, SHIO_INTERNAL, "input is too large for JNI");
        return nullptr;
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(length));
    if (result != nullptr && length > 0) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(length),
                                reinterpret_cast<const jbyte*>(data));
    }
    return result;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass local = env->FindClass(kExceptionClass);
    if (local != nullptr) {
        g_exception_class = static_cast<jclass>(env->NewGlobalRef(local));
        env->DeleteLocalRef(local);
        if (g_exception_class != nullptr) {
            g_exception_ctor =
                env->GetMethodID(g_exception_class, "<init>", kExceptionSignature);
        }
    }
    // 类缺失不应阻止库加载；调用出错时会退化为 RuntimeException。
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return;
    }
    {
        std::lock_guard<std::mutex> lock(g_table_mutex);
        for (auto& entry : g_handles) {
            std::lock_guard<std::mutex> handle_lock(entry.second->mutex);
            if (!entry.second->destroyed) {
                entry.second->destroyed = true;
                shio_kernel_destroy(entry.second->kernel);
            }
        }
        g_handles.clear();
    }
    if (g_exception_class != nullptr) {
        env->DeleteGlobalRef(g_exception_class);
        g_exception_class = nullptr;
        g_exception_ctor = nullptr;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_top_zxionf_shio_kernel_NativeShio_abiVersion(JNIEnv* /*env*/, jobject /*self*/) {
    return static_cast<jint>(shio_abi_version());
}

// 内核版本（编译期包版本，如 "0.2.0"）。指针指向进程级静态存储，直接复制成 jstring。
extern "C" JNIEXPORT jstring JNICALL
Java_top_zxionf_shio_kernel_NativeShio_kernelVersion(JNIEnv* env, jobject /*self*/) {
    const char* version = shio_version();
    if (version == nullptr) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(version);
}

extern "C" JNIEXPORT jlong JNICALL
Java_top_zxionf_shio_kernel_NativeShio_open(JNIEnv* env, jobject /*self*/, jstring path,
                                            jbyteArray password, jbyteArray options_json) {
    std::string path_utf8;
    if (!get_string(env, path, &path_utf8)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "database path is null or invalid");
        return 0;
    }

    std::vector<std::uint8_t> password_bytes;
    if (!get_bytes(env, password, &password_bytes)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "password is null or invalid");
        return 0;
    }
    ByteEraser password_eraser{password_bytes};

    std::vector<std::uint8_t> options_bytes;
    const bool has_options = options_json != nullptr;
    if (has_options && !get_bytes(env, options_json, &options_bytes)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "options JSON is invalid");
        return 0;
    }

    shio_kernel* kernel = nullptr;
    const std::int32_t rc = shio_open(
        path_utf8.c_str(), data_or_empty(password_bytes), password_bytes.size(),
        has_options ? data_or_empty(options_bytes) : nullptr,
        has_options ? options_bytes.size() : 0, &kernel);
    if (rc != SHIO_OK || kernel == nullptr) {
        throw_kernel_error(env, rc != SHIO_OK ? rc : SHIO_INTERNAL);
        return 0;
    }

    auto handle = std::make_shared<Handle>();
    handle->kernel = kernel;
    const jlong id = g_next_id.fetch_add(1);
    std::lock_guard<std::mutex> lock(g_table_mutex);
    g_handles.emplace(id, std::move(handle));
    return id;
}

extern "C" JNIEXPORT void JNICALL
Java_top_zxionf_shio_kernel_NativeShio_close(JNIEnv* env, jobject /*self*/, jlong id) {
    auto handle = acquire(id);
    if (handle == nullptr) {
        // 已 destroy 或从未存在的句柄：重复关闭视为成功。
        return;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        return;
    }
    const std::int32_t rc = shio_close(handle->kernel);
    if (rc != SHIO_OK) {
        throw_kernel_error(env, rc);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_top_zxionf_shio_kernel_NativeShio_destroy(JNIEnv* env, jobject /*self*/, jlong id) {
    std::shared_ptr<Handle> handle;
    {
        std::lock_guard<std::mutex> lock(g_table_mutex);
        auto it = g_handles.find(id);
        if (it == g_handles.end()) {
            return;
        }
        handle = it->second;
        g_handles.erase(it);
    }
    {
        // 摘表后此锁保证所有已进入的调用都已完成。
        std::lock_guard<std::mutex> lock(handle->mutex);
        if (handle->destroyed) {
            return;
        }
        handle->destroyed = true;
    }
    shio_kernel_destroy(handle->kernel);
    handle->kernel = nullptr;
    (void)env;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_top_zxionf_shio_kernel_NativeShio_insert(JNIEnv* env, jobject /*self*/, jlong id,
                                              jstring namespace_name, jstring collection,
                                              jbyteArray json) {
    std::string name;
    std::string coll;
    if (!get_string(env, namespace_name, &name) || !get_string(env, collection, &coll)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "namespace or collection is null or invalid");
        return nullptr;
    }
    std::vector<std::uint8_t> value;
    if (json == nullptr || !get_bytes(env, json, &value)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "value JSON is null or invalid");
        return nullptr;
    }

    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return nullptr;
    }

    std::uint8_t out_id[16] = {};
    std::uint8_t* meta = nullptr;
    std::size_t meta_length = 0;
    const std::int32_t rc =
        shio_insert(handle->kernel, name.c_str(), coll.c_str(), data_or_empty(value),
                    value.size(), out_id, &meta, &meta_length);
    if (rc != SHIO_OK) {
        if (meta != nullptr) {
            shio_free(meta, meta_length);
        }
        throw_kernel_error(env, rc);
        return nullptr;
    }
    if (meta != nullptr) {
        shio_free(meta, meta_length);
    }
    return bytes_to_array(env, out_id, sizeof(out_id));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_top_zxionf_shio_kernel_NativeShio_put(JNIEnv* env, jobject /*self*/, jlong id,
                                           jstring namespace_name, jstring collection,
                                           jbyteArray record_id, jbyteArray json) {
    std::string name;
    std::string coll;
    if (!get_string(env, namespace_name, &name) || !get_string(env, collection, &coll)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "namespace or collection is null or invalid");
        return nullptr;
    }
    if (record_id == nullptr) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "record id is null");
        return nullptr;
    }
    std::vector<std::uint8_t> id_bytes;
    std::vector<std::uint8_t> value;
    if (!get_bytes(env, record_id, &id_bytes) || id_bytes.size() != 16) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "record id must be 16 bytes");
        return nullptr;
    }
    if (json == nullptr || !get_bytes(env, json, &value)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "value JSON is null or invalid");
        return nullptr;
    }

    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return nullptr;
    }

    std::uint8_t* meta = nullptr;
    std::size_t meta_length = 0;
    const std::int32_t rc =
        shio_put(handle->kernel, name.c_str(), coll.c_str(), id_bytes.data(),
                 data_or_empty(value), value.size(), &meta, &meta_length);
    if (rc != SHIO_OK) {
        if (meta != nullptr) {
            shio_free(meta, meta_length);
        }
        throw_kernel_error(env, rc);
        return nullptr;
    }
    return copy_and_free(env, meta, meta_length);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_top_zxionf_shio_kernel_NativeShio_get(JNIEnv* env, jobject /*self*/, jlong id,
                                           jstring namespace_name, jstring collection,
                                           jbyteArray record_id) {
    std::string name;
    std::string coll;
    if (!get_string(env, namespace_name, &name) || !get_string(env, collection, &coll)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "namespace or collection is null or invalid");
        return nullptr;
    }
    if (record_id == nullptr) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "record id is null");
        return nullptr;
    }
    std::vector<std::uint8_t> id_bytes;
    if (!get_bytes(env, record_id, &id_bytes) || id_bytes.size() != 16) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "record id must be 16 bytes");
        return nullptr;
    }

    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return nullptr;
    }

    std::uint8_t* output = nullptr;
    std::size_t output_length = 0;
    const std::int32_t rc = shio_get(handle->kernel, name.c_str(), coll.c_str(),
                                     id_bytes.data(), &output, &output_length);
    if (rc != SHIO_OK) {
        if (output != nullptr) {
            shio_free(output, output_length);
        }
        throw_kernel_error(env, rc);
        return nullptr;
    }
    return copy_and_free(env, output, output_length);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_zxionf_shio_kernel_NativeShio_delete(JNIEnv* env, jobject /*self*/, jlong id,
                                              jstring namespace_name, jstring collection,
                                              jbyteArray record_id) {
    std::string name;
    std::string coll;
    if (!get_string(env, namespace_name, &name) || !get_string(env, collection, &coll)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "namespace or collection is null or invalid");
        return JNI_FALSE;
    }
    if (record_id == nullptr) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "record id is null");
        return JNI_FALSE;
    }
    std::vector<std::uint8_t> id_bytes;
    if (!get_bytes(env, record_id, &id_bytes) || id_bytes.size() != 16) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "record id must be 16 bytes");
        return JNI_FALSE;
    }

    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return JNI_FALSE;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return JNI_FALSE;
    }

    std::int32_t existed = 0;
    const std::int32_t rc = shio_delete(handle->kernel, name.c_str(), coll.c_str(),
                                        id_bytes.data(), &existed);
    if (rc != SHIO_OK) {
        throw_kernel_error(env, rc);
        return JNI_FALSE;
    }
    return existed != 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_top_zxionf_shio_kernel_NativeShio_list(JNIEnv* env, jobject /*self*/, jlong id,
                                            jstring namespace_name, jstring collection,
                                            jbyteArray after, jint limit,
                                            jboolean include_deleted) {
    std::string name;
    std::string coll;
    if (!get_string(env, namespace_name, &name) || !get_string(env, collection, &coll)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "namespace or collection is null or invalid");
        return nullptr;
    }
    if (limit < 0) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "limit must not be negative");
        return nullptr;
    }
    std::vector<std::uint8_t> after_bytes;
    const bool has_after = after != nullptr;
    if (has_after && (!get_bytes(env, after, &after_bytes) || after_bytes.size() != 16)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "after must be a 16-byte record id");
        return nullptr;
    }

    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return nullptr;
    }

    std::uint8_t* output = nullptr;
    std::size_t output_length = 0;
    const std::int32_t rc = shio_list(
        handle->kernel, name.c_str(), coll.c_str(),
        has_after ? after_bytes.data() : nullptr, static_cast<std::size_t>(limit),
        include_deleted == JNI_TRUE ? 1 : 0, &output, &output_length);
    if (rc != SHIO_OK) {
        if (output != nullptr) {
            shio_free(output, output_length);
        }
        throw_kernel_error(env, rc);
        return nullptr;
    }
    return copy_and_free(env, output, output_length);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_top_zxionf_shio_kernel_NativeShio_batch(JNIEnv* env, jobject /*self*/, jlong id,
                                             jstring namespace_name, jstring collection,
                                             jbyteArray ops_json) {
    std::string name;
    std::string coll;
    if (!get_string(env, namespace_name, &name) || !get_string(env, collection, &coll)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "namespace or collection is null or invalid");
        return nullptr;
    }
    if (ops_json == nullptr) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "batch operations JSON is null");
        return nullptr;
    }
    std::vector<std::uint8_t> operations;
    if (!get_bytes(env, ops_json, &operations)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "batch operations JSON is invalid");
        return nullptr;
    }

    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return nullptr;
    }

    std::uint8_t* meta = nullptr;
    std::size_t meta_length = 0;
    const std::int32_t rc =
        shio_batch(handle->kernel, name.c_str(), coll.c_str(), data_or_empty(operations),
                   operations.size(), &meta, &meta_length);
    if (rc != SHIO_OK) {
        if (meta != nullptr) {
            shio_free(meta, meta_length);
        }
        throw_kernel_error(env, rc);
        return nullptr;
    }
    return copy_and_free(env, meta, meta_length);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_top_zxionf_shio_kernel_NativeShio_stats(JNIEnv* env, jobject /*self*/, jlong id) {
    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return nullptr;
    }

    std::uint8_t* output = nullptr;
    std::size_t output_length = 0;
    const std::int32_t rc = shio_stats(handle->kernel, &output, &output_length);
    if (rc != SHIO_OK) {
        if (output != nullptr) {
            shio_free(output, output_length);
        }
        throw_kernel_error(env, rc);
        return nullptr;
    }
    return copy_and_free(env, output, output_length);
}

// 改主口令：内核只重包裹密钥，不重写记录；旧口令此后不再可用。
extern "C" JNIEXPORT void JNICALL
Java_top_zxionf_shio_kernel_NativeShio_changePassword(JNIEnv* env, jobject /*self*/, jlong id,
                                                     jbyteArray new_password) {
    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return;
    }
    if (new_password == nullptr) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "new password is null");
        return;
    }
    std::vector<std::uint8_t> password_bytes;
    if (!get_bytes(env, new_password, &password_bytes)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "new password is invalid");
        return;
    }
    ByteEraser password_eraser{password_bytes};

    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return;
    }

    const std::int32_t rc = shio_change_password(
        handle->kernel, data_or_empty(password_bytes), password_bytes.size());
    if (rc != SHIO_OK) {
        throw_kernel_error(env, rc);
    }
}

// 在线备份：内核把当前库复制到 out_path（目标必须不存在），返回摘要 JSON。
extern "C" JNIEXPORT jbyteArray JNICALL
Java_top_zxionf_shio_kernel_NativeShio_backup(JNIEnv* env, jobject /*self*/, jlong id,
                                             jstring out_path) {
    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return nullptr;
    }
    std::string path_utf8;
    if (!get_string(env, out_path, &path_utf8)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "backup path is null or invalid");
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return nullptr;
    }

    std::uint8_t* output = nullptr;
    std::size_t output_length = 0;
    const std::int32_t rc = shio_backup(handle->kernel, path_utf8.c_str(), &output, &output_length);
    if (rc != SHIO_OK) {
        if (output != nullptr) {
            shio_free(output, output_length);
        }
        throw_kernel_error(env, rc);
        return nullptr;
    }
    return copy_and_free(env, output, output_length);
}

// 从备份恢复到新文件，返回新句柄（登记到句柄表）；不碰调用方已有句柄。
extern "C" JNIEXPORT jlong JNICALL
Java_top_zxionf_shio_kernel_NativeShio_restore(JNIEnv* env, jobject /*self*/, jstring backup_path,
                                              jstring target_path, jbyteArray password,
                                              jbyteArray options_json) {
    std::string backup_utf8;
    if (!get_string(env, backup_path, &backup_utf8)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "backup path is null or invalid");
        return 0;
    }
    std::string target_utf8;
    if (!get_string(env, target_path, &target_utf8)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "target path is null or invalid");
        return 0;
    }
    std::vector<std::uint8_t> password_bytes;
    if (password == nullptr || !get_bytes(env, password, &password_bytes)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "password is null or invalid");
        return 0;
    }
    ByteEraser password_eraser{password_bytes};

    std::vector<std::uint8_t> options_bytes;
    const bool has_options = options_json != nullptr;
    if (has_options && !get_bytes(env, options_json, &options_bytes)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "options JSON is invalid");
        return 0;
    }

    shio_kernel* kernel = nullptr;
    const std::int32_t rc = shio_restore(
        backup_utf8.c_str(), target_utf8.c_str(), data_or_empty(password_bytes),
        password_bytes.size(), has_options ? data_or_empty(options_bytes) : nullptr,
        has_options ? options_bytes.size() : 0, &kernel);
    if (rc != SHIO_OK || kernel == nullptr) {
        throw_kernel_error(env, rc != SHIO_OK ? rc : SHIO_INTERNAL);
        return 0;
    }

    auto restored = std::make_shared<Handle>();
    restored->kernel = kernel;
    const jlong new_id = g_next_id.fetch_add(1);
    std::lock_guard<std::mutex> lock(g_table_mutex);
    g_handles.emplace(new_id, std::move(restored));
    return new_id;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_top_zxionf_shio_kernel_NativeShio_syncEnable(JNIEnv* env, jobject /*self*/, jlong id,
                                                  jbyteArray device_id) {
    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return nullptr;
    }
    std::vector<std::uint8_t> device_bytes;
    if (device_id != nullptr && !get_bytes(env, device_id, &device_bytes)) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "device id is invalid");
        return nullptr;
    }
    // 内核按固定 16 字节读取 device_id（DeviceId::from_bytes），不传长度。
    if (device_id != nullptr && device_bytes.size() != 16) {
        throw_shio(env, SHIO_INVALID_ARGUMENT, "device id must be 16 bytes");
        return nullptr;
    }
    const bool has_device = device_id != nullptr;

    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return nullptr;
    }

    std::uint8_t* output = nullptr;
    std::size_t output_length = 0;
    const std::int32_t rc = shio_sync_enable(
        handle->kernel, has_device ? device_bytes.data() : nullptr, &output, &output_length);
    if (rc != SHIO_OK) {
        if (output != nullptr) {
            shio_free(output, output_length);
        }
        throw_kernel_error(env, rc);
        return nullptr;
    }
    return copy_and_free(env, output, output_length);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_top_zxionf_shio_kernel_NativeShio_syncInfo(JNIEnv* env, jobject /*self*/, jlong id) {
    auto handle = acquire(id);
    if (handle == nullptr) {
        throw_closed(env);
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    if (handle->destroyed) {
        throw_closed(env);
        return nullptr;
    }

    std::uint8_t* output = nullptr;
    std::size_t output_length = 0;
    const std::int32_t rc = shio_sync_info(handle->kernel, &output, &output_length);
    if (rc != SHIO_OK) {
        if (output != nullptr) {
            shio_free(output, output_length);
        }
        throw_kernel_error(env, rc);
        return nullptr;
    }
    return copy_and_free(env, output, output_length);
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_zxionf_shio_kernel_NativeShio_lastError(JNIEnv* env, jobject /*self*/, jlong /*id*/) {
    // shio_last_error 是线程局部的，不需要内核句柄。
    const std::string message = read_last_error();
    return env->NewStringUTF(message.c_str());
}
