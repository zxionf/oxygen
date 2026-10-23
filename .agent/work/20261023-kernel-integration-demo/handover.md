# 20261023-kernel-integration-demo 交接

## 当前进度

P0–P3 完成，进入 P4 设备验收。P2 的 JNI 薄层与 P3 的演示界面都已在模拟器验证；arm64 真机、文件锁与杀进程重开尚未验证。

## P2 摘要（JNI 薄层）

- `app/src/main/cpp/CMakeLists.txt`：`IMPORTED_NO_SONAME` 链接 `jniLibs/<abi>/libshio_ffi.so`，产出 `libshio_jni.so`；C++17、静态 C++ 运行时、`-fno-exceptions -fno-rtti`。
- `shio_jni.cpp`：`id -> Handle` 表、每句柄互斥量、`destroy` 摘表后等待在途调用；UTF-16→UTF-8；输出复制后 `shio_free`；口令清零；全部错误码映射为 `ShioException`。
- `NativeShio.kt` 校验 `abiVersion == 1`；`ShioDatabase.kt` 单线程调度、UUID 转换、打开/关闭/重建语义。
- `app/src/debug/.../SmokeActivity.kt` + `scripts/smoke.sh`：脚本化冒烟，不进入 release。

## P3 实现（演示界面）

### 状态层

- `app/src/main/java/top/zxionf/shio/demo/DemoViewModel.kt`
  - `AndroidViewModel` + `StateFlow<DemoUiState>`，包含口令、草稿、路径、ABI、统计、记录、选中项、busy、状态与错误。
  - 打开/创建与重新打开共用 `openOrCreate()`；关闭后保留路径与口令，重开时 `ShioDatabase.open` 先销毁旧句柄。
  - `refresh()`：`list` 限 50 条，逐条 `get` 取正文；`NOT_FOUND` 的条目跳过。
  - `addNote()` / `deleteSelected()` / `batchWrite()` / `showStats()`。
  - `batchWrite()` 用 `batch` 一次写 3 条，ID 由客户端按 UUIDv7 布局生成（时间戳 + version 7 + variant 10 + 随机位）。
  - `onCleared()` 用独立 IO 作用域 `database.destroy()`，不阻塞主线程。
  - 所有数据库调用都走 `ShioDatabase` 的 suspend API，内部切到单线程 IO；`NativeShio` 初始化也发生在 IO 线程。
- `ShioDatabase.kt` 新增 `suspend fun abiVersion()`，避免主线程触发 native 库加载。

### 界面

- `app/src/main/java/top/zxionf/shio/demo/DemoScreen.kt`
  - 单页 `LazyColumn`：口令 + 打开/创建/重新打开/关闭、路径与 ABI、统计、刷新、批量写入 3 条、添加、记录列表、删除选中、状态行、错误卡片。
  - 错误展示为 `错误 INVALID_KEY (-3)` + 内核消息。
  - 关闭后隐藏操作区，只保留“重新打开”和 `SHIO_CLOSED` 提示。
  - `DemoActions` 回调集合让 `DemoContent` 可以零依赖预览。

### 依赖

- 新增 `lifecycle-viewmodel-compose`、`lifecycle-runtime-compose`（2.9.4）与显式 `compose.foundation`（BOM 管版本）。
- 未引入 DI、导航或序列化库；JSON 仍用 `org.json`。

## 验证记录

- `./gradlew :app:assembleDebug`：BUILD SUCCESSFUL。
- `./gradlew :app:lintDebug`：0 errors / 4 warnings（版本提示 3 条 + `mipmap-anydpi-v26` 的 `ObsoleteSdkInt`）。
- `scripts/smoke.sh`：`SMOKE OK abi=1 ... format=1 db_bytes=1056768`。
- 模拟器：AVD `Medium_Phone`，Android 17（API 37），x86_64，16 KB page（`PAGE_SIZE=16384`）。
- 手工 UI 验证（截图为 `/sdcard/p3-*.png`）：
  - 打开/创建后显示路径、`ABI 版本：1`、`format_version/key_id/db_bytes`。
  - 添加 1 条、批量写入 3 条后共 4 条；删除选中后 3 条；刷新提示“共 3 条（最多 50 条）”。
  - 统计按钮更新 `db_bytes`（关闭/重开后由 1056768 变为 61440/65536，属内核整理行为）。
  - 关闭后显示“重新打开”；重开后 3 条记录仍在。
  - 错误口令显示 `INVALID_KEY (-3)`，改回正确口令后可恢复。
  - 横竖屏切换后打开状态、记录、统计与口令输入保留。
  - 按返回退出后重新启动，再打开同一库成功（`onCleared` 释放句柄生效）。
- 无 `AndroidRuntime` 崩溃记录。

## P4 注意事项

1. **arm64 真机**：安装同一个 debug APK，重复打开、增删、批量写、关闭/重开、旋转与杀进程重开。
2. **杀进程重开**：`adb shell am force-stop top.zxionf.shio.debug` 后重新启动，确认数据仍在；进程内句柄随进程释放，不需要额外清理。
3. **INVALID_KEY**：已在模拟器验证，真机复测即可。
4. **CLOSED**：已由冒烟覆盖（关闭后 insert 返回 `CLOSED`，重复 close 不崩溃）；界面目前关闭后隐藏写入入口，如需界面可见，可临时保留操作按钮或走冒烟。
5. **LOCKED**：尚未验证。建议给 `SmokeActivity` 增加一个 `--es path <path>` 或 debug-only probe，在 MainActivity 已打开 `filesDir/shio-demo.db` 时用第二个句柄打开同一路径，断言返回 `SHIO_LOCKED`。
6. **stats 一致性**：记录操作前后的 `format_version`、`key_id`、`db_bytes`，确认与内核输出一致；`db_bytes` 在关闭/重开时可能变化，属正常整理。
7. **UI 自动化**：参考 `.agent/notes.md` 的 P3 命令；截图为 1080x2400，坐标按截图换算。`input keyevent 4` 在键盘已收起时会退出 Activity，不要用它收键盘。
8. 验收完成后把设备型号、系统版本、结果写入本文件，并更新 `tasks.md`、`STATE.md` 与 `SPEC.md`。

## 遗留风险

- JNI 使用 `-fno-exceptions`，内存分配失败会直接 abort；Demo 规模可接受。
- `destroy` 与在途调用的互斥逻辑经过冒烟，未做并发压力测试。
- `onCleared` 的清理是异步的：退出后立刻重开存在极小概率撞上旧句柄释放未完成；如出现 `LOCKED`，先确认是否为该场景。
- 口令以 `String` 形式存在于 ViewModel 内存中，无法清零；不写日志、不落盘。
- `batch` 的 UUIDv7 随机位使用 `ThreadLocalRandom`，不是加密随机源；记录 ID 不承担安全职责。
