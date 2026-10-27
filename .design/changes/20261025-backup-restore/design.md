# 20261025-backup-restore

## 背景

密码库目前只有一份 `filesDir/vault.db`：换机、误删应用、刷机都会永久丢数据。内核早已提供同设备备份与恢复（`shio_backup` / `shio_restore`，存储维护 S2），JNI 一直没映射。

## 备份与恢复目标

在设置里提供「备份密码库」与「从备份恢复」，用系统文件选择器（SAF）落到用户选的位置；恢复前必须显式确认并输入备份的主口令。

## 备份流程

- 入口：`设置 → 数据 → 备份密码库`。
- 流程：
  1. 打开 SAF `CreateDocument`（MIME `application/octet-stream`，默认文件名 `shio-backup-<yyyyMMdd-HHmm>.db`）；
  2. 内核把备份写到应用缓存目录的临时文件（`cacheDir/backup-tmp.db`），返回摘要 JSON（`format_version`、`db_bytes`、`table_count`、`row_count`）；
  3. 应用把临时文件流式拷贝到用户选定的 `content://` URI；
  4. 删除临时文件。
- 成功：Snackbar「已备份到 <文件名>」；失败按 `ShioException` 码给文案，临时文件必定清理。
- 备份文件用**原库主口令**打开，不携带明文与密钥；这一点在界面文案里说明（「恢复时需要当时的主口令」）。

## 恢复流程

- 入口：`设置 → 数据 → 从备份恢复`。
- 流程：
  1. SAF `OpenDocument` 选备份文件；
  2. 弹确认对话框：说明会**替换当前全部条目**、且必须输入该备份的主口令；输入框用 `AppTextField`；
  3. 把 URI 流式拷贝到 `cacheDir/restore-tmp.db`；
  4. 调用 `shio_restore(tmp, filesDir/vault-restored.db, password)`：目标文件必须不存在（先删陈旧残留），内核校验口令与结构，成功返回**新句柄**；
  5. 成功后：销毁当前句柄与当前 `vault.db` → 把 `vault-restored.db` 改名为 `vault.db` → 清空 `VaultSession` → 导航回解锁页（用备份的主口令解锁）；
  6. 任一步失败：删除临时文件与 `vault-restored.db`，**当前 `vault.db` 与句柄保持不变**，用户仍可继续使用。
- 失败文案：`INVALID_KEY (-3)` →「备份的主口令不正确」；`CORRUPTED (-10)` →「备份文件损坏」；`INVALID_ARGUMENT (-1)` →「目标文件已存在」（应用内部已规避）；`IO`/`DATABASE` 单独提示。

## 为什么恢复要「重启会话」

内核的 `shio_restore` 返回的是指向**新文件**的句柄，而恢复后的库口令是备份当时的那个。继续复用旧会话会让「句柄 → 文件」与「口令 → 会话」错位，因此恢复成功后统一：销毁句柄、替换文件、清会话、回到解锁页，由用户用备份口令重新解锁。这样状态永远只有一条真实路径。

## 备份与恢复界面

- 设置页新增分组「数据」，两行：`备份密码库`（副标题「保存到文件，用当前主口令加密」）、`从备份恢复`（副标题「替换当前数据，需要备份的主口令」）。
- 两个入口都在已解锁状态才可达（设置页本身受 `RequireUnlocked` 守卫）。
- 备份进行中禁用入口并显示加载；恢复对话框在提交中禁用按钮。

## 实现要点

- `shio_jni.cpp`：`NativeShio_backup(handle, outPath)`（返回摘要 JSON，输出缓冲区 `shio_free`）、`NativeShio_restore(backupPath, targetPath, password, optionsJson)`（返回新句柄并登记到句柄表）。
- `NativeShio` / `ShioDatabase`：`backupTo(path): String`、`restoreFrom(backupPath, targetPath, password): Long`；口令转 `ByteArray` 后清零。
- `VaultRepository`：`backup(outPath)` 直接透传；`restoreInPlace(backupPath, password)` 负责「临时目标 → 校验通过 → 换文件 → 清会话」的整个事务性流程。
- SAF 用 `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument / OpenDocument)`；流拷贝放 `Dispatchers.IO`。
- 不改内核、不改存储格式；备份文件与 `vault.db` 同构（含 `format=2`）。

## 边界

- 不做云盘自动上传、不做定时备份、不做导入导出（CSV/JSON 属第二波 C22）。
- 备份文件落在用户选的位置，应用不再持有；文件本身是加密的，但没有口令提示与找回。
- 恢复只支持本应用同格式备份（`format=1/2` 均可，但会替换当前库）。

## 备份与恢复验证

- 备份到本地文件 → 删掉一条记录 → 从备份恢复 → 条目回到备份时的状态，需要输入备份主口令。
- 恢复时故意输错口令 → 报 `INVALID_KEY`，当前库与条目不受影响。
- 恢复后杀进程重开：新库可解锁、`db_id` 与备份时一致（可在「诊断信息」里看）。
- 备份/恢复过程中界面不假死，失败后仍可正常使用。
