# 20261025-diagnostics-entry

## 背景

「设置 → 关于」里的 `存储格式` 与 `同步身份` 两行只在 debug 显示（决策 #49），release 用户与线上排障看不到库的格式版本和 `db_id`。但这两个值恰恰是判断「库是不是 format=1 旧库」「两端是不是同一个库」的关键信息。

## 诊断入口目标

在 release 上也能看到库的格式与身份，但**不进入普通界面**：放在一个需要主动触发的诊断入口里。

## 交互

- 入口：`设置 → 关于 → 版本` 这一行**长按**（单击无反应，不改变现有视觉）。
- 弹出 `AlertDialog`（`extraLarge` 圆角，与现有对话框一致），标题「诊断信息」，内容为 `StackedList` 键值行：
  - `存储格式`：`format=2`
  - `同步身份`：`db_id`（32 位十六进制；库未启用同步时显示「未启用」）
  - `设备身份`：`device_id`（同上）
  - `内核版本` / `ABI 版本`
  - `数据库`：完整路径
- 动作：`复制`（写入剪贴板并打敏感标记，便于反馈问题时粘贴）与 `关闭`。
- 未解锁（`session == null`）时长按不弹窗；此时也不存在可展示的会话信息。

## 与 debug 的关系

- 原「关于」页在 debug 下多出的两行**保留不动**：debug 一眼可见，release 走长按入口。
- 诊断弹窗在 debug 与 release 都可用，内容一致。

## 实现要点

- `SettingsScreen`：给「版本」行加 `Modifier.pointerInput { detectTapGestures(onLongPress = ...) }`（`combinedClickable` 需要额外 opt-in，这里用 `detectTapGestures` 更直接）。
- 弹窗内容由一个新的 `remember` 计算，数据来自 `VaultComponent.repository.session` 与 `kernel/KernelInfo`；不新增依赖、不改数据层。
- 复制复用详情页同样的敏感标记写法（`ClipData` + `EXTRA_IS_SENSITIVE`），避免复制实现出现第二套。

## 不在范围

- 不新增设置项、不加可见按钮或提示。
- 不做日志导出、不做诊断上报。
- 不改 `VaultSession` 与内核侧任何行为。

## 兼容与影响

- 纯 UI 增量，无存储格式、无导航变化；长按是既有行上的新手势，不影响单击行为。
- release 仍然不把 `db_id` 放进普通可见界面，符合决策 #49 的初衷。

## 验证记录（2026-10-25，真机 10AE4N38SA000LK）

- debug：解锁 → 设置 → 长按「版本」→ 弹窗显示 `format=2`、`db_id=cadd9d18…`、`device_id=b47a5065…`、内核版本 `0.2.0`、ABI `1`、数据库路径。
- release（签名包 `top.zxionf.shio`）：首次建库 `rel12345` → 设置 → 长按「版本」→ 弹窗显示 `format=2`、`db_id=14250a0e…`、`device_id=6e7a9405…`；「复制」与「关闭」均正常，页面不崩。
- release 的「关于」列表仍是 7 行（存储格式/同步身份不在可见列表里），符合决策 #49 的初衷。
