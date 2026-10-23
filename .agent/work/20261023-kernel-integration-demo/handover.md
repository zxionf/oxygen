# 20261023-kernel-integration-demo 交接

## 当前进度

P0–P4 全部完成并已归档：脚手架、JNI 薄层、演示界面、双设备验收均通过，`sele close` 已把 design 折叠进 SPEC。剩余工作只有发布准备（签名、包体、Linux 同步脚本），不属于本次变更范围。

## 已完成内容

### P1 脚手架

- Gradle 8.14.3 / AGP 8.13.0 / Kotlin 2.2.20，Compose 单页可构建、可安装。
- 两个 ABI 的 `libshio_ffi.so` 入库，16 KB LOAD 对齐校验通过。

### P2 JNI 薄层

- `app/src/main/cpp/CMakeLists.txt`：`IMPORTED_NO_SONAME` 链接 `jniLibs/<abi>/libshio_ffi.so`，产出 `libshio_jni.so`；C++17、静态 C++ 运行时、`-fno-exceptions -fno-rtti`。
- `shio_jni.cpp`：`id -> Handle` 表、每句柄互斥量、`destroy` 摘表后等待在途调用；UTF-16→UTF-8；输出复制后 `shio_free`；口令清零；全部错误码映射为 `ShioException`。
- `NativeShio.kt` 校验 `abiVersion == 1`；`ShioDatabase.kt` 单线程调度、UUID 转换、打开/关闭/重建语义。

### P3 演示界面

- `DemoViewModel` + `StateFlow`：打开/创建、关闭、重开、刷新、增删、批量写、统计、错误状态。
- 单页 Compose：口令、路径与 ABI、记录列表、批量写入 3 条、选中删除、错误卡片、关闭/重开。
- 所有 native 调用在 `Dispatchers.IO.limitedParallelism(1)`；主线程不触 native。
- 横竖屏切换后打开状态、列表、统计与口令输入保留；`onCleared` 用独立 IO 作用域释放句柄。
- `batch` 的记录 ID 由客户端按 UUIDv7 布局生成。

### P4 验收工具

- `app/src/debug/.../SmokeActivity.kt`：open → insert → get → list → stats → close → CLOSED → 重复 close → INVALID_KEY → 正确口令重开；stats 一致性断言。
- `app/src/debug/.../AcceptanceActivity.kt`：
  - `lock`：同进程两个句柄打开同一路径，第二个返回 `SHIO_LOCKED`。
  - `persist-write` / `persist-verify`：写入 marker → 杀进程 → 重新打开验证。
- `scripts/smoke.sh`、`scripts/acceptance.sh`：串起构建、安装、日志采集与断言。

## 验收记录

### 设备

| 设备 | 系统 | ABI | Page size |
|---|---|---|---|
| `vivo V2359A`（PD2309M） | Android 16 / API 36 | `arm64-v8a` | 4 KB |
| 模拟器 `sdk_gphone16k_x86_64` | Android 17 / API 37 | `x86_64` | 16 KB |

### 结果

- `scripts/acceptance.sh <serial>` 在两台设备均通过：
  - `SMOKE OK abi=1 ... format=1 db_bytes=1056768`
  - `ACCEPT OK mode=lock code=-8`
  - `ACCEPT OK mode=persist-write ...` → force-stop → `ACCEPT OK mode=persist-verify records=1 format=1`
- 真机 UI 全流程：打开/创建、添加、批量写入 3 条、删除选中、关闭、重新打开、统计；错误口令显示 `INVALID_KEY (-3)`；杀进程后重开，3 条记录仍在。
- 模拟器 UI 全流程与横竖屏切换通过；批量写后立即 force-stop，重开仍是 4 条。
- APK：两个 ABI 的 `libshio_ffi.so` 与 `libshio_jni.so` 均存在；`libshio_jni.so` 的 `DT_NEEDED` 为 `libshio_ffi.so`，LOAD 对齐 `0x4000`；`zipalign -P 16` 通过。
- `assembleDebug`、`lintDebug`（0 errors / 4 warnings）通过。
- `sele lint` 通过，任务 33/33。

### 观察点

- P4 开始前，模拟器演示库出现过一次“打开后 0 条记录”。随后重复 force-stop、`install -r`、批量写 + 立即杀进程均正常保留数据，未能复现。已记录为单次观察；若再次出现，需要回到 hydrogen 检查 redb 恢复/持久化路径。

## 归档

- 已把 SPEC 的“分层与目录结构”与 design 的“工程结构”合并为一份当前结构，并执行 `sele close 20261023-kernel-integration-demo`。
- SPEC 已折叠 design 的 8 个小节，`Active Change` 复位为“无”；`.design/changes/` 与 `.agent/work/` 按 sele 语义原地保留。
- 折叠后手工修正了 SPEC 的 `batch` UUIDv7 说明、重开语义与构建产物版本描述。
- `sele lint` 通过。

## 后续（发布准备）

1. 合并 SPEC 的“工程结构”与“分层与目录结构”，执行 `sele close`。
2. 补 Linux 版 `scripts/sync-shio.sh`（当前只有 Windows 的 `sync-shio.ps1`）。
3. 发布签名、混淆与包体检查。
4. 若继续做业务功能，另立变更。

## 遗留风险

- JNI 使用 `-fno-exceptions`，内存分配失败会直接 abort；Demo 规模可接受。
- `destroy` 与在途调用的互斥逻辑经过冒烟与杀进程验证，未做并发压力测试。
- 口令以 `String` 形式存在于 ViewModel 内存中，无法清零；不写日志、不落盘。
- `batch` 的 UUIDv7 随机位使用 `ThreadLocalRandom`，不是加密随机源。
- 真机首次打开可能遇到旧演示库口令未知；验收时可 `pm clear` 后重来。
