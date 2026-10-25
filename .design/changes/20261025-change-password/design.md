# 20261025-change-password

## 背景

内核从 0.1 起就暴露了 `shio_change_password`（重包裹主口令派生出的密钥，不重写记录），但 JNI 一直没有映射，应用里也就没有入口。用户一旦怀疑主口令泄漏，目前只能删库重建，等于丢数据。

## 修改主口令目标

在设置里提供「修改主口令」，调用内核 `shio_change_password`，改完立刻生效、需要重启验证新口令；失败时给出可读错误，且不影响现有数据。

## 交互

- 入口：`设置 → 安全 → 修改主口令`（与「自动锁定」同一分组）。
- 页面：独立路由 `change-password`（受 `RequireUnlocked` 保护），M3E 结构：
  - 顶栏：返回 + 标题「修改主口令」
  - 说明文案：新口令用于下次解锁，忘记无法找回，条目内容不受影响
  - 字段：`新主口令`、`确认新主口令`（`AppTextField`，线框式，带显隐切换）
  - 底部主按钮「修改主口令」，提交中禁用并显示加载
- 校验：新口令至少 8 个字符；两次必须一致；为空/过短/不一致都给内联错误。
- 成功：Snackbar「主口令已修改」，返回设置页；提示「下次解锁请使用新口令」。
- 失败：按 `ShioException` 错误码给文案，其中 `BUSY (-7)` 提示「上次密钥轮换未完成，请重试」。

## 不要求当前主口令的原因

会话已经处于解锁状态（`VaultRepository.session != null`），说明本次启动已用当前口令通过内核校验；内核的 `change_password` 也只接受新口令。要求再输一次当前口令只能防「别人拿到已解锁手机」这一种场景，而这种场景下对方本就能看到全部条目，防护收益很低，反而要写「先销毁句柄再重新校验」这种破坏性流程。

因此：**只要求新口令 + 确认**；这一点写进 DECISIONS。

## 实现要点

- `shio_jni.cpp`：新增 `Java_..._NativeShio_changePassword(env, self, id, jbyteArray newPassword)`，沿用 `acquire` + `std::lock_guard` + `throw_kernel_error` 模式。
- `NativeShio.changePassword(handle: Long, newPassword: ByteArray)`。
- `ShioDatabase.changePassword(newPassword: String)`：转 `ByteArray` 后 `finally` 清零，走单线程 IO 调度器。
- `VaultRepository.changePassword(newPassword: String)`：`requireUnlocked()` + 调 `ShioDatabase`，不改 `VaultSession`（句柄与 `db_id` 不变）。
- 路由：`Routes.ChangePassword = "change-password"`，`AppNavHost` 里包一层 `RequireUnlocked`；设置页「安全」分组里加一行。
- 新页面 `ui/settings/ChangePasswordScreen.kt`。

## 边界

- 不做口令强度规则（只保留 ≥8 字符，与创建页一致）。
- 不做「显示当前口令」、不做口令提示。
- 不改内核、不改存储格式；`format=2` 下内核会重包裹全部 `keys2` 行，记录内容原样保留。

## 修改主口令验证

- 修改成功后锁屏重开：旧口令失败（`INVALID_KEY (-3)`），新口令成功。
- 新口令少于 8 字符、两次不一致：内联错误，不提交。
- 修改前后条目数量与内容不变。
- 修改过程中没有崩溃、没有日志泄漏口令。
