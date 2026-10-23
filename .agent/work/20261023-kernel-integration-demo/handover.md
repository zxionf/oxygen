# 20261023-kernel-integration-demo 交接

## 本轮完成

### P0 产物

- `app/src/main/jniLibs/arm64-v8a/libshio_ffi.so`、`app/src/main/jniLibs/x86_64/libshio_ffi.so`
  来自 hydrogen `50e073b3998c690811414aa9534393b4033cac35`（与本机 hydrogen HEAD 一致）。
- 校验结果（`readelf`）：
  - arm64：`AArch64`，3 个 LOAD 段对齐均 `0x4000`，sha256 `063d6bef…6f6c2c`，3,020,912 字节
  - x86_64：`Advanced Micro Devices X86-64`，3 个 LOAD 段对齐均 `0x4000`，sha256 `8d0df685…9e1598`，3,183,912 字节
  - 动态依赖只有 `libdl/libm/libc`，无 `libc++_shared`，无需额外打包
  - 导出符号齐全（`shio_abi_version`、`shio_open`、`shio_close`、`shio_kernel_destroy`、`shio_insert`、`shio_put`、`shio_get`、`shio_delete`、`shio_list`、`shio_batch`、`shio_stats`、`shio_last_error`、`shio_free`）
- `scripts/sync-shio.ps1`：校验 hydrogen HEAD == 固定提交 → `cargo ndk -t arm64-v8a -t x86_64 build --release -p shio-ffi`（带 `-Wl,-z,max-page-size=16384`）→ 复制 → `llvm-readelf` 校验架构与对齐 → 打印 sha256。
  - `-SkipBuild` 路径已实测通过；构建路径未实测（本机缺 `cargo-ndk`）。

### P1 脚手架

- Gradle：wrapper 8.14.3（sha256 `bd711022…5f3531`）、AGP 8.13.0、Kotlin 2.2.20、Compose BOM 2025.10.00。
- `app`：`compileSdk 36` / `minSdk 29` / `targetSdk 36`，`ndkVersion 29.0.14206865`，ABI 仅 `arm64-v8a`、`x86_64`，`applicationId top.zxionf.shio`，debug 后缀 `.debug`。
- `packaging.jniLibs.useLegacyPackaging = false`，APK 内 `.so` 由 `zipalign -c -P 16` 验证通过（两个 ABI 均 OK）。
- 页面：`MainActivity`（edge-to-edge）→ `ShioTheme` → `DemoScreen`（占位，Scaffold + TopAppBar + 两行文案）。
- 无任何权限声明；`allowBackup=false` + `data_extraction_rules` 排除备份。

## 验证记录

- `gradlew.bat :app:assembleDebug`：BUILD SUCCESSFUL。
- `gradlew.bat :app:lintDebug`：0 errors / 4 warnings（3 条为版本可升级提示，1 条为 `mipmap-anydpi-v26` 的 `ObsoleteSdkInt`）。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，含 `lib/{arm64-v8a,x86_64}/libshio_ffi.so`，`zipalign -P 16` 校验成功。
- 模拟器：AVD `shio_x86_64`（API 35，x86_64）。`adb install -r` 成功，`am start -W` 返回 `Status: ok`，`topResumedActivity` 为 `top.zxionf.shio.debug/top.zxionf.shio.MainActivity`，无 FATAL，界面渲染正常（截图 `build/tmp/p1b.png`）。
- APK 内 `.so` 经 AGP strip：3.0 MB→2.1 MB（arm64）、3.18 MB→2.52 MB（x86_64），导出符号与 `0x4000` 对齐保留，哈希因此与源产物不同，属预期。

## 环境注意

- 构建前必须设 `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`（系统默认是 JDK 23）。
- 本机 WSL 无 `git`、无 `cargo-ndk`；`NDK 27.0.12077973` 目录为空，勿选用。
- 细节与踩坑见 `.agent/notes.md`。

## 下一步（P2）

1. `app/src/main/cpp/CMakeLists.txt`：`IMPORTED` 目标指向 `jniLibs/<abi>/libshio_ffi.so`，产出 `libshio_jni.so`；同时在 `app/build.gradle.kts` 补 `externalNativeBuild`。
2. `shio_jni.cpp`：句柄表 + 每句柄互斥量，`destroy` 摘表后等待在途调用；输出缓冲区复制后 `shio_free`；口令清零；错误码映射 `ShioException`。
3. Kotlin 侧 `NativeShio.kt`（校验 `abiVersion == 1`）与 `ShioDatabase.kt`（`Dispatchers.IO.limitedParallelism(1)`）。
4. 冒烟：模拟器上 open → insert → get → list → close。
