# 20261025-diagnostics-entry 任务清单

原则：每个任务做完即构建；UI 改动必须满足 `.design/SPEC.md` 的「M3E 设计基线」。

## D1 诊断入口

- [x] 「设置 → 关于 → 版本」行长按触发（单击行为不变，`detectTapGestures(onLongPress)`）
- [x] 未解锁 / 无会话时不弹窗（`session != null` 才置 `diagnosticsVisible`）

## D2 诊断弹窗

- [x] `AlertDialog`（`extraLarge`）标题「诊断信息」，内容用 `StackedList`（`surfaceContainerHigh`）
- [x] 行：存储格式、同步身份（`db_id`）、设备身份（`device_id`）、内核版本、ABI 版本、数据库路径
- [x] 取值缺失时显示「未启用」
- [x] 动作：「复制」（`ClipData` + `EXTRA_IS_SENSITIVE`）与「关闭」

## D3 验证

- [x] debug 真机：长按版本 → 弹窗内容与「关于」里的两行一致
- [x] release 真机：长按版本 → 能看到 `format=2` 与 `db_id`（截图受 `FLAG_SECURE` 限制，用控件树核对）
- [x] 「复制」与「关闭」可用，页面不崩
- [x] `:app:assembleDebug` + `:app:assembleRelease` + `:app:lintDebug` + `:app:testDebugUnitTest` 通过
