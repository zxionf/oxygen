# 20261025-change-password 任务清单

原则：实现完成后不跑构建与测试，验证项由用户执行（见「验证」）。

## P1 内核映射

- [x] `shio_jni.cpp` 新增 `NativeShio_changePassword(handle, newPassword)`（`acquire` + `lock_guard` + `throw_kernel_error`，本地口令副本 `std::fill` 清零）
- [x] `NativeShio` 增加 `external fun changePassword(handle: Long, newPassword: ByteArray)`
- [x] `ShioDatabase.changePassword(newPassword: String)`：单线程 IO + `ByteArray` 清零
- [x] `VaultRepository.changePassword(newPassword: String)`：未解锁先拦，透传错误码

## P2 界面

- [x] `Routes.ChangePassword = "change-password"`，`AppNavHost` 注册并包 `RequireUnlocked`
- [x] 设置页「安全」分组新增「修改主口令」行（`Key` 图标 + 「下次解锁使用新口令」）
- [x] 新增 `ui/settings/ChangePasswordScreen.kt`：两个 `AppTextField` + 校验 + 提交中禁用 + Snackbar
- [x] 成功返回设置；`BUSY`/`CLOSED`/`READ_ONLY`/`CORRUPTED`/`IO` 等错误码给可读文案

## P3 文档

- [x] `design.md` 记录「不要求当前口令」的理由与错误码文案
- [x] 追加决策记录（#64）

## 模拟器冒烟（AI 执行，2026-10-25）

- [x] 设置 → 安全 → 修改主口令 页可达，布局为两个线框输入框 + 底部胶囊按钮
- [x] 输入新口令 + 确认 → 提交 → 回到设置页
- [x] 锁定后用**旧**口令解锁 → `主口令不正确（INVALID_KEY (-3)）`
- [x] 用**新**口令解锁 → 进入列表，条目仍在（GitHub 等）
- [x] 新口令少于 8 字符 → 内联错误「新主口令至少 8 个字符」，停留本页不提交

## 验证（用户执行）

- [x] 真机改口令后锁屏重开：旧口令失败、新口令成功（用户验收 2026-10-25）
- [x] 两次不一致时的内联错误（用户验收 2026-10-25）
- [x] 改口令前后条目数量与内容不变（用户验收 2026-10-25）
- [x] `:app:assembleDebug` 与 `:app:lintDebug` 通过（用户本地构建）

## P4 归档（用户验证通过后）

- [x] 更新 `SPEC.md`（新增「修改主口令」；MVP 范围移除该限制；JNI 接口表补 `changePassword` 与两个 sync 符号）
- [x] `sele close --dry-run` 确认全部 `appended` 后执行 `sele close`
