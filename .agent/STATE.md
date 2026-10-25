# Agent State

Active Change: 20261023-password-vault（M12 实现完成，待真机冒烟）
Phase: 创建/解锁/锁定已接内核：VaultComponent 进程级会话、LockScreen 调 create/unlock、锁定 destroy 后清栈、RequireUnlocked 守卫、关于页 debug 诊断
Blocked: 真机 10AE4N38SA000LK 未连接（只有 emulator-5554，不使用）；create → lock → unlock 冒烟未做
Next: 真机冒烟（创建 → 锁定 → 错误口令 → 正确解锁 → 关于页 format=2/db_id），然后进 M13 把列表切到 VaultRepository
Note: 新建库为 format=2（sync_enable 已调用，无同步 UI）；界面改动先对照 SPEC 的 M3E 基线
