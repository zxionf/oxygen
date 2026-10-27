# 20261025-backup-restore 任务清单

原则：实现完成后由 AI 在带窗口的模拟器冒烟（用户可现场观察），真机验收与最终确认由用户执行。

## B1 内核映射

- [x] `shio_jni.cpp`：`NativeShio_backup(handle, outPath)` → 返回摘要 JSON（输出用 `shio_free`）
- [x] `shio_jni.cpp`：`NativeShio_restore(backupPath, targetPath, password, optionsJson)` → 返回新句柄并登记句柄表
- [x] `NativeShio`：`backup(handle, outPath): ByteArray`、`restore(...): Long`
- [x] `ShioDatabase.backupTo(path): String`、`restoreToVerifiedFile(...): Long`（口令 `ByteArray` 后清零）
- [x] `ShioDatabase.destroyHandle(id)`：释放恢复校验用的临时句柄，归还文件锁与 fd

## B2 仓库

- [x] `VaultRepository.backup(outPath): BackupSummary`（`format_version`/`db_bytes`/`table_count`/`row_count`）
- [x] `VaultRepository.restoreInPlace(backupPath, password)`：旁路文件 `vault-restored.db` → 内核校验 → 释放旧句柄 → 删旧库与 `.lock` → rename → 清 `session`
- [x] 失败时不改现有 `vault.db` 与句柄，并清理旁路文件与其 `.lock`（实测失败后只剩 `vault.db`）

## B3 界面

- [x] 设置页新增「数据」分组：`备份密码库`、`从备份恢复` 两行
- [x] 备份：SAF `CreateDocument`（默认名 `shio-backup-<时间>.db`）→ 内核写临时文件 → 拷到 URI → 删临时文件 → Snackbar
- [x] 恢复：SAF `OpenDocument` → 确认对话框（说明会替换全部数据 + 输入备份主口令）→ 拷到缓存 → `restoreInPlace` → 回解锁页
- [x] 进行中禁用入口并显示加载；错误按 `ShioException` 码给文案并附内核原始描述
- [x] 恢复成功后 Snackbar「已恢复，请用备份时的主口令解锁」并清栈回解锁页
- [x] 失败时删除 SAF 刚建出的空文件，不留 0 字节残留

## B4 外部交互豁免（实测发现的必修项）

- [x] `AutoLockController.beginExternalInteraction()` / `endExternalInteraction()`
- [x] 外部交互期间 `onStop` 只记时间戳，不立即锁定；`onStart` 用 `max(偏好, 120 秒)` 宽限判定
- [x] 备份/恢复的 SAF 启动前 `begin`、回调里 `end`
- [x] 实测：自动锁定「立即」下也能正常完成备份（此前 SAF 一弹出就销毁句柄，备份必然失败）

## 文档

- [x] `design.md` 记录恢复为何要重启会话、临时文件与失败保护、外部交互豁免
- [x] 追加决策记录（#65 外部交互豁免、#66 恢复替换事务）

## 验证（AI 在带窗口模拟器冒烟，用户可观察）

- [x] 备份到 Download 成功，文件 1056768 字节（与库同大小）
- [x] 删除「GitHub」→ 从备份恢复 → 解锁后 GitHub 回来（8 条完整）
- [x] 恢复输错口令 → `INVALID_KEY (-3)`，停留在对话框，库不受影响
- [x] 取消恢复 → 列表数据不变
- [x] 恢复成功后 `files/` 只剩 `vault.db`，无 `vault-restored*` 残留
- [x] `:app:assembleDebug` 通过

## B5 归档

- [x] 更新 `SPEC.md`：新增「备份与恢复」；JNI 表补 `backup`/`restore`；ABI 表把两者移到已映射；MVP 范围移除备份限制；「自动锁定」补外部交互豁免
- [x] `sele close --dry-run` 确认全部 `appended` 后执行 `sele close`
