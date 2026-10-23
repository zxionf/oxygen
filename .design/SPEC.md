# Spec

<!-- 当前设计真相源：一个概念一节，只写现状。历史见 git，取舍理由见 DECISIONS.log。 -->

## 项目定位

- shio-android：shio 内核的 Android 应用端，仓库位于 `oxygen`。
- 内核（加密持久化、同步）由 `hydrogen` 仓库提供；本仓库负责 Android 应用层、内核集成与交互。
- 当前范围是最小内核集成 Demo：打开/创建、增删、列表、批量写、统计、关闭与重开。

## 技术栈

- Kotlin 2.2.20、Jetpack Compose（BOM 2025.10.00）、Material 3；单 Activity、单 `app` 模块。
- Gradle 8.14.3（wrapper + sha256）、AGP 8.13.0、JDK 21。
- `compileSdk` / `targetSdk` 36，`minSdk` 29，`applicationId` 为 `top.zxionf.shio`（debug 构建加 `.debug` 后缀）。
- ABI 仅 `arm64-v8a`、`x86_64`；NDK `29.0.14206865`；CMake `3.22.1`。
- JNI 薄层使用 C++17，静态 C++ 运行时，关闭 C++ 异常与 RTTI。
- 不引入 DI 框架、导航库与序列化库；JSON 使用系统 `org.json`。

## 约束

- 内核以 hydrogen C ABI v1（`crates/shio-ffi/include/shio.h`）为准，JNI 启动时校验 `shio_abi_version == 1`。
- `jniLibs` 下的 `libshio_ffi.so` 入库，来源固定 hydrogen 提交 `50e073b3998c690811414aa9534393b4033cac35`；两份产物均满足 16 KB LOAD 对齐。
- 所有 native 调用在 `Dispatchers.IO.limitedParallelism(1)` 上串行执行，主线程不调用 native。
- 口令以字节数组传入 native，调用结束立即清零；口令、明文与记录正文不写日志。
- 无权限声明；`allowBackup=false`；release 构建不包含冒烟入口。

## 内核集成

- 加载顺序：`System.loadLibrary("shio_ffi")` → `System.loadLibrary("shio_jni")`。
- CMake 用 `IMPORTED` 目标链接 `jniLibs/<abi>/libshio_ffi.so`；因源库没有 SONAME，使用 `IMPORTED_NO_SONAME`，产物 `libshio_jni.so` 的 `DT_NEEDED` 为 `libshio_ffi.so`。
- 句柄对 Kotlin 是不透明 `Long`；JNI 内部维护 `id -> Handle` 表，每个 `Handle` 持有 `shio_kernel*`、互斥量与销毁标记。
- `destroy` 先从表中摘除，再取互斥量等待在途调用，最后调用 `shio_kernel_destroy`。
- 内核分配的输出缓冲区在 JNI 内复制后立即 `shio_free`；非 `SHIO_OK` 返回码连同 `shio_last_error` 文本抛出 `ShioException(code, message)`。

## 数据模型

- Demo 命名空间固定 `demo`，集合固定 `notes`。
- 记录值固定 `{"text":"<用户输入>"}`。
- 记录 ID 使用内核生成的 UUIDv7；Kotlin 侧在 16 字节原始值与 36 字符标准 UUID 字符串之间转换。
- `list` 只返回元信息，界面逐条 `get` 取正文，单次最多 50 条。
- 数据库路径为应用私有目录 `filesDir/shio-demo.db`。

## 演示界面

- 单页 Compose：口令与打开/创建、记录列表、添加/删除、批量写入 3 条、关闭/重开/刷新/统计、状态与错误区。
- 打开选项固定 `{"create":true}`；错误按 `ShioException.code` 展示。
- 状态由 `StateFlow` 提供，Compose 只消费状态。

## 分层与目录结构

```
app/src/main/java/top/zxionf/shio/
├── MainActivity.kt
├── kernel/
│   ├── NativeShio.kt        # external 声明与库加载
│   ├── ShioDatabase.kt      # 句柄、线程与 UUID
│   └── ShioException.kt     # code + message
├── demo/DemoScreen.kt       # Compose 单页
└── ui/theme/
app/src/main/cpp/
├── shio_jni.cpp             # JNI 薄层
├── CMakeLists.txt
├── include/shio.h           # hydrogen C ABI 只读副本
└── README.md
app/src/main/jniLibs/<abi>/libshio_ffi.so
app/src/debug/               # SmokeActivity，仅 debug 变体
scripts/smoke.sh             # 设备冒烟脚本
```
