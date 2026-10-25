# Agent State

Active Change: 20261023-password-vault（M13 完成）
Phase: 列表/搜索/排序已切到 vault.db（真机验证通过）；详情与编辑器仍是 FakeVaultStore
Blocked: 无（真机 10AE4N38SA000LK 可用；emulator-5554 不使用）
Next: M14 真实详情（按 ID 读取、复制敏感标记、http/https 外链、真实 delete + 列表刷新）
Note: debug 解锁后会用样例条目播种空库（只播一次，release 不播）；新建库为 format=2
