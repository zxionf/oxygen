# 20261025-clipboard-auto-clear 任务清单

原则：实现完成后不跑构建与测试，所有验证项由用户执行（见「C 验证」）。

## C1 统一复制入口

- [x] 新增 `vault/SensitiveClipboard.kt`：`copy(context, label, value)` / `clearNow()` / 常量 `CLEAR_AFTER_MILLIS`
- [x] `copy` 写入 `ClipData`，API 33+ 打 `EXTRA_IS_SENSITIVE`
- [x] `copy` 记录写入内容并起 30 秒计时；重复复制会取消上一轮计时
- [x] `clearNow` 先比对当前剪贴板内容，一致才清，并取消计时

## C2 接入调用方

- [x] `EntryDetailScreen`：删除私有 `copySensitiveText`，四处复制（用户名/密码/备注/连接）改走 `SensitiveClipboard`
- [x] `SettingsScreen`：诊断弹窗的「复制」改走 `SensitiveClipboard`，删除本地 `copySensitive`
- [x] `AutoLockController.lockNow()`：销毁句柄后调用 `SensitiveClipboard.clearNow()`（手动与自动锁定共用）
- [x] 复制成功的 Snackbar 改成「已复制 X，30 秒后自动清除」，让用户知道有时限

## C3 文档

- [x] `design.md` 写清规则与取舍（30 秒固定、不清后台、读取失败时宁可多清、进程被杀不留计时）
- [x] 追加决策记录（集中入口 + 不清后台的理由 + 只清自己内容的判定）

## C 验证（用户执行，AI 不跑）

- [x] 复制密码 → 30 秒内切到别处能粘贴（模拟器：立即粘贴得到 `gh-fake-password-1`）
- [x] 复制密码 → 超过 30 秒粘贴为空（模拟器：前台等 35 秒后粘贴无内容）
- [x] 复制密码 → 立刻复制普通文字 → 30 秒后普通文字仍在（未误清；用户验收 2026-10-25）
- [x] 锁定后立刻粘贴为空（模拟器：立即自动锁 → 回前台已在解锁页且剪贴板已清）
- [x] 复制时剪贴板浮层/输入法不显示明文（`EXTRA_IS_SENSITIVE`）
- [x] `:app:assembleDebug` 与 `:app:lintDebug` 通过（用户本地构建）

## C4 归档（用户验证通过后）

- [x] 更新 `SPEC.md` 的「安全措施」与「密码库 MVP 范围」（归档时完成，新增「剪贴板与敏感复制」一节）
- [x] `sele close --dry-run` 确认全部 `appended` 后执行 `sele close`

## C5 真机反馈后的加固（2026-10-25）

- [x] `copy` 记录 `elapsedRealtime` 时间戳
- [x] `MainActivity.onStart` 调 `SensitiveClipboard.clearIfExpired()`（进程冻结后回前台补清）
- [x] 清除失败（后台被拒）保留 `pendingText`，等下次重试
- [x] `clearPrimaryClip()` 无效时用空文本覆盖（兜底 ROM 忽略清除的情况）
- [x] 模拟器复验：前台 35 秒清空、切后台 40 秒回来清空
- [x] 真机复验（用户，2026-10-25 验收通过）：复制密码 → 系统「长按 → 粘贴」等 35 秒为空
- [x] 真机复验（用户，2026-10-25 验收通过）：复制密码 → 切到别的应用 30 秒内可粘贴
- [x] 真机复验（用户，2026-10-25 验收通过）：复制密码 → 立刻复制普通文字 → 35 秒后普通文字仍在

## C6 输入法剪贴板提示（真机确认系统粘贴已清、输入法历史仍有）

- [x] `SensitiveClipboard.consumeImeNotice(context)`：独立 `clipboard` 预置文件，只提示一次
- [x] 详情页首次复制后弹「输入法可能保留一份副本」提示（`AlertDialog` + `extraLarge`）
- [x] 弹窗动作：「知道了」与「打开输入法设置」（`Settings.ACTION_INPUT_METHOD_SETTINGS`）
- [x] 设置页诊断复制不弹该提示
- [x] 模拟器验证：首次复制弹窗、关闭后可继续复制、二次复制不再弹、「打开输入法设置」跳到系统输入法页面
- [x] 真机复验（用户，2026-10-25 验收通过）：首次复制看到提示；系统粘贴 35 秒后为空；输入法剪贴板历史属 ROM 行为，应用不处理
