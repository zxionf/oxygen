# Agent State

Active Change: 20261023-password-vault（M11 实现完成，待真机验 gate 分流）
Phase: M11 数据层就位：VaultPaths/VaultRepository/VaultEntryCodec + JNI 映射 sync_enable/sync_info + gate 按 vault.db 分流
Blocked: 真机 10AE4N38SA000LK 未连接（只有 emulator-5554，不使用）；gate 分流与 X-建库尚未真机验证
Next: 真机验：清数据后启动应进「创建主口令」页（无 vault.db）；然后进 M12 把创建/解锁/锁定接到 VaultRepository
Note: 建库即 format=2（sync_enable 已启用，无同步 UI）；界面改动先对照 SPEC 的 M3E 基线；构建跑 :app:assembleDebug :app:assembleRelease :app:lintDebug :app:testDebugUnitTest
