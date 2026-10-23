# 20261023-kernel-integration-demo 任务清单

## P0 产物与环境

- [x] 复制 hydrogen 提交 `50e073b` 的 `libshio_ffi.so` 到 `app/src/main/jniLibs/{arm64-v8a,x86_64}/`
- [x] 校验两份 `.so` 的 ABI、ELF 架构与 16 KB page 对齐（三处 LOAD 均 `0x4000`）
- [x] 记录 JDK、NDK、SDK 版本与构建命令到 `.agent/notes.md`
- [x] 编写 `scripts/sync-shio.ps1`：固定 hydrogen 提交、`cargo-ndk` 构建、复制产物（已用 `-SkipBuild` 验证复制/校验路径；`cargo-ndk` 构建路径待安装后验证）

## P1 工程脚手架

- [x] 建立 Gradle Kotlin DSL 工程：`settings.gradle.kts`、根 `build.gradle.kts`、`gradle/libs.versions.toml`、wrapper
- [x] 建立 `app` 模块：manifest、主题、`MainActivity`、空 Compose 页面
- [x] 配置 `compileSdk 36` / `minSdk 29` / `targetSdk 36`、ABI 过滤、NDK 版本
- [x] 用 JBR 21 跑通 `assembleDebug`，并在模拟器安装启动（AVD `shio_x86_64`，API 35）
- [ ] CMake 配置随 P2 的 `CMakeLists.txt` 一起加入（当前 `app/build.gradle.kts` 无 `externalNativeBuild`，避免引用不存在的构建脚本）

## P2 JNI 薄层

- [ ] `CMakeLists.txt` 以 IMPORTED 目标链接 `libshio_ffi.so`，产出 `libshio_jni.so`
- [ ] `shio_jni.cpp`：句柄表、每句柄互斥量、`destroy` 等待在途调用
- [ ] 字符串/字节数组转换、输出缓冲区复制后 `shio_free`、口令字节清零
- [ ] 错误码到 `ShioException(code, message)` 的映射，覆盖 `SHIO_CLOSED` 等常用码
- [ ] `NativeShio.kt` 声明全部接口并校验 `abiVersion == 1`
- [ ] `ShioDatabase.kt`：单线程调度、UUID 转换、打开/关闭/重建语义
- [ ] 在真机或模拟器上完成一次 open → insert → get → list → close 的脚本化冒烟

## P3 演示界面

- [ ] 口令输入 + 打开/创建，展示路径与 ABI 版本
- [ ] 列表加载（list + 逐条 get，限 50 条）与刷新
- [ ] 添加、删除、批量写入 3 条
- [ ] 关闭、重新打开、统计展示
- [ ] 错误区展示错误码与消息
- [ ] 主线程无 native 调用，旋转屏幕后状态可恢复

## P4 验收

- [ ] `x86_64` 模拟器：完整操作 + 杀进程后重开数据仍在
- [ ] `arm64-v8a` 真机：完整操作 + 杀进程后重开数据仍在
- [ ] 错误口令返回 `SHIO_INVALID_KEY`，界面可见
- [ ] 关闭后写入返回 `SHIO_CLOSED`；重复关闭不崩溃
- [ ] 文件锁场景：第二个实例打开同一库返回 `SHIO_LOCKED`（可用双进程或测试代码验证）
- [ ] `stats` 的 `format_version`、`key_id`、`db_bytes` 与操作结果一致
- [ ] 记录设备型号、系统版本与结果到 `.agent/work/20261023-kernel-integration-demo/handover.md`

## 完成定义

- [ ] `assembleDebug` 通过，两个 ABI 的 `.so` 都进入 APK
- [ ] 两套设备上的验收项全部通过
- [ ] JNI 层没有内核逻辑复制，所有缓冲区正确释放
- [ ] `.design` 与 `.agent` 状态同步，`sele lint` 通过
