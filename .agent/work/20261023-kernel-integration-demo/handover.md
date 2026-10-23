# 20261023-kernel-integration-demo 交接

## 本轮完成（P2 JNI 薄层）

### 构建与链接

- `app/src/main/cpp/CMakeLists.txt`：`IMPORTED` 目标指向 `jniLibs/<abi>/libshio_ffi.so`，产出 `libshio_jni.so`；C++17、`-Wall -Wextra`、`-fno-exceptions -fno-rtti`、`ANDROID_STL=c++_static`。
- `IMPORTED_NO_SONAME TRUE` + `target_link_directories`：源 `.so` 没有 SONAME，若直接按绝对路径链接，`DT_NEEDED` 会写入构建机路径，设备加载失败。现在两个 ABI 的 `libshio_jni.so` 依赖均为 `libshio_ffi.so`。
- `app/build.gradle.kts` 增加 `externalNativeBuild`，CMake 版本 `3.22.1`。
- `app/src/main/cpp/include/shio.h`：hydrogen `50e073b` 的只读副本，sha256 `ba156f49…a119846`。

### JNI 实现

- `shio_jni.cpp`：全局 `id -> shared_ptr<Handle>` 表；`Handle` 持有 `shio_kernel*`、`std::mutex`、`destroyed` 标记。
- `destroy` 先摘表 → 取句柄锁等待在途调用 → 置销毁标记 → 调 `shio_kernel_destroy`；等待中的调用拿到锁后看到标记，返回 `SHIO_CLOSED`。
- `close` 可重复；对已销毁句柄是空操作。写入已关闭但未销毁的库返回 `SHIO_CLOSED`。
- UTF-16 → UTF-8 手写转换（拒绝内嵌 NUL 与孤立代理项）；字节数组显式带长度。
- 所有输出先复制到 `jbyteArray` 再 `shio_free`；错误路径也检查并释放非空缓冲区。
- 口令副本用 `volatile` 逐字节清零；`shio_last_error` 读取后映射到 `ShioException(code, message)`，覆盖 ABI 全部错误码。
- `JNI_OnLoad` 缓存 `ShioException` 类与 `(I, String)` 构造器；类缺失时退化为 `RuntimeException`。

### Kotlin 侧

- `NativeShio.kt`：`object` + external 声明；加载 `shio_ffi`、`shio_jni` 后校验 `abiVersion == 1`。
- `ShioDatabase.kt`：`Dispatchers.IO.limitedParallelism(1)`；`open` 自动销毁旧句柄；`close` 保留句柄、`destroy` 释放；UUID 16 字节 ↔ 标准字符串；JSON 输出统一转 `String`。
- `ShioException.kt`：`code + message`，附全部错误码常量。
- `app/src/debug/.../SmokeActivity.kt`：仅 debug 的脚本化冒烟，结果写 logcat `ShioSmoke`；`scripts/smoke.sh` 负责构建、安装与采集。

## 验证记录

- `./gradlew :app:assembleDebug`：BUILD SUCCESSFUL；未设置 `JAVA_HOME` 也可用（系统 java 21.0.13）。
- `./gradlew :app:lintDebug`：0 errors / 4 warnings（版本可升级提示 3 条 + `mipmap-anydpi-v26` 的 `ObsoleteSdkInt`）。
- APK 内 `lib/{arm64-v8a,x86_64}/libshio_jni.so` 均存在；`readelf -lW` 的 3 个 LOAD 段对齐均为 `0x4000`。
- `zipalign -c -P 16 -v 4 app-debug.apk`：Verification successful。
- 设备：AVD `Medium_Phone`，Android 17（API 37），x86_64，`getconf PAGE_SIZE` = 16384。
- `scripts/smoke.sh` 输出：

```
SMOKE OK abi=1 id=01a168ba-327b-7202-a239-e10c138df11a format=1 db_bytes=1056768
```

冒烟覆盖：open → insert → get（meta id 校验）→ list（next 校验）→ stats（format_version/key_id/db_bytes）→ close → 关闭后写入得到 `SHIO_CLOSED` → 重复 close → 重新 open 后数据仍在 → destroy（含重复 destroy）。

## 环境（Arch Linux）

- 通过 `sdkmanager` 安装 NDK `29.0.14206865` 与 CMake `3.22.1`，与 `ndkVersion` / 决策 8 保持一致。
- 细节与踩坑见 `.agent/notes.md` 的 Linux 段。
- `scripts/sync-shio.ps1` 仍是 Windows 专用；Linux 侧的同步脚本尚未补。

## P3 注意事项

1. `DemoViewModel.kt` 使用 `StateFlow` 暴露状态；所有数据库调用走 `ShioDatabase` 的 suspend API，主线程不触 native。
2. `list` 返回的 `next` 是 UUID 字符串或 `null`；`items` 只有元信息，正文需逐条 `get`（限 50 条）。
3. 打开失败（如错误口令）会抛 `ShioException`，按 `code` 展示；`INVALID_KEY` 的界面验证属于 P4。
4. 旋转屏幕后状态恢复：ViewModel + `SavedStateHandle`，不要持有 `ShioDatabase` 的裸句柄到 UI。
5. 关闭后重开：直接 `ShioDatabase.open` 会销毁旧句柄；界面上的“重新打开”无需手动 destroy。
6. 数据库路径为 `filesDir/shio-demo.db`；冒烟使用的 `filesDir/smoke/` 与演示库互不影响。

## 遗留风险

- JNI 使用 `-fno-exceptions`，`std::vector`/`std::string` 分配失败会直接 abort；Demo 规模可接受。
- `destroy` 与在途调用的互斥逻辑经过冒烟，但未做并发压力测试。
- `lastError()` 依赖线程局部状态，调度器不保证同一线程，仅作排查用途。
- 尚未在 arm64 真机验证；P4 进行。
