# 20261027-multi-vault

## 背景

现在只有一个固定的 `filesDir/vault.db`，想「工作一套、个人一套」分开口令无从下手；也缺少删除整套库的入口（只能清应用数据）。多密码库要解决的是：多个互相独立的加密库、各自的显示名、启动时能选、能重命名与删除。

## 库索引

- 索引存在 `SharedPreferences`（预置名 `vaults`，键 `entries`），值是一个 JSON 数组，每项：
  ```json
  {"id":"<uuid>","name":"工作","fileName":"<uuid>.db","createdAt":1730000000000}
  ```
- 索引**只含显示名与文件名**，不含口令、条目或任何敏感内容；库本身仍由内核加密。
- 索引随每次增删改立即落盘（`SharedPreferences.edit().apply()`）。

## 文件布局

- 每个库一个文件：`filesDir/vaults/<id>.db`，内核的旁路锁是同目录的 `<id>.db.lock`。
- 旧版本的 `filesDir/vault.db` **原地保留**，首次启动时自动注册为一条索引记录：
  `id = "default"`、`name = "默认密码库"`、`fileName = "vault.db"`（不移动文件，避免大文件复制与失败风险）。
- 新建库时 `id` 用 `UUID.randomUUID()`；文件名即 `<id>.db`。

## 启动分流

- 索引为空（且没有旧 `vault.db`）→ `create`（创建第一个库）。
- 索引恰好一条 → 直接进 `unlock?vaultId=<id>`（保持现在的「打开即解锁」体验）。
- 索引多条 → 进 `vaults`（库列表），由用户选一条进入 `unlock?vaultId=<id>`。
- 记住上次打开的库（`SharedPreferences` 键 `lastVaultId`）：解锁成功后写入；仅供库列表高亮与「继续上次」提示，不改变上面的分流规则。

## 页面与路由

| 路由 | 内容 |
|---|---|
| `vaults` | 库列表：每行显示名称、条目数（未解锁不显示）、创建时间；行点击进解锁；右上「新建库」；行尾菜单可重命名/删除 |
| `unlock?vaultId={id}` | 解锁指定库；标题下显示库名；失败时提示该库 |
| `create` | 创建新库：库名（默认「我的密码库」）+ 主口令 + 确认 |
| `vault` 及之后 | 不变（进入的是「当前已解锁的库」） |

- 路由参数 `vaultId` 缺失时回退到「索引第一条」，保证旧调用与深链不崩。
- 库列表页受「无需解锁即可进入」保护：它不读库内容，因此不需要会话；但行尾的条目数需要解锁后才能算，未解锁时该行不显示条目数。

## 库管理

- **重命名**：只改索引里的 `name`，不动文件。
- **删除**：`AlertDialog` 二次确认（提示「库内全部条目将被永久删除，且不可恢复」）；删除 `<id>.db`、`<id>.db.lock` 与该索引记录；若删除的是当前已解锁的库，先锁定（`AutoLockController.lockNow()`）再删。
- **限制**：不允许删除「正在使用且未锁定」的库（先锁定）；允许删到 0 个库，此时下次启动回到创建页。

## 旧库处理

- 旧安装升级后：`vault.db` 自动成为 `default` 库，用户看到的名称是「默认密码库」，可在库列表里重命名。
- 备份/恢复（C13）仍作用于**当前已解锁的库**：备份用当前库路径，恢复替换当前库文件；多库下路径由 `VaultLibrary` 提供。
- 导入导出（C22）与标签（C21）不受影响，都基于当前库。

## 新增文件

- `vault/VaultLibrary.kt`：索引模型 `VaultLibraryEntry(id, name, fileName, createdAt)` + 读写（`list()`、`register()`、`rename()`、`remove()`、`ensureLegacyRegistered()`）+ `fileFor(context, entry)`。
- `ui/vault/VaultListPickerScreen.kt`（库列表页，命名避免与 `VaultListScreen` 混淆）。
- `Routes.Vaults = "vaults"`、`Routes.unlock(vaultId)`、`Routes.VaultIdArg`。

## 不做的事

- 不做跨库移动条目、不做库导出到另一台设备（备份/恢复逐个库做）。
- 不做库密码提示、不做库图标/颜色。
- 不做「同时解锁多个库」：任一时刻只有一个活动会话（内核句柄唯一，`VaultComponent.repository` 单例）。
- 不做旧 `vault.db` 迁移到 `vaults/` 目录（原地保留，减少失败面）。

## 覆盖场景

- 全新安装：直接进创建页；创建后库列表只有一条，下次启动直接进该库解锁页。
- 已有旧 `vault.db` 升级：启动直接进解锁页，库名为「默认密码库」，解锁后条目完整。
- 新建第二个库：库列表出现两条；分别解锁互不影响（A 库条目不出现在 B 库）。
- 重命名库：列表名称变化，解锁页标题同步；条目不受影响。
- 删除库：文件与索引都消失；删除当前库会先锁定并回到库列表/创建页。
- 杀进程重开：库列表与上次解锁的库仍然正确。
