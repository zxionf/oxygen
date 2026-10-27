# 20261027-multi-vault 任务清单

原则：实现完成后由 AI 在带窗口模拟器冒烟（用户可观察），真机验收由用户执行。

## V1 索引层

- [x] `vault/VaultLibrary.kt`：`VaultLibraryEntry(id, name, fileName, createdAt)` + `isLegacy`
- [x] `list` / `decodeEntries`（坏项跳过、重复 ID 去重、按创建时间排序）
- [x] `ensureLegacyRegistered`：旧 `filesDir/vault.db` 自动注册为 `default`（幂等）
- [x] `register` / `rename` / `remove`（先删索引再删文件，避免孤儿索引）
- [x] `fileFor` / `prepareFile`（**确保父目录存在**，否则内核 `open(create=true)` 报 `IO (-14)`）
- [x] `lastVaultId` / `rememberLast` / `forgetId`
- [x] 单测 `VaultLibraryTest` 6 例

## V2 路由与页面

- [x] `Routes.Vaults`、`VaultIdArg`、`UnlockPattern`、`unlock(id)`
- [x] `AppNavHost`：`unlock` 改为可选参数模板；`gate` 与 `RequireUnlocked` 都按索引分流
- [x] `ui/vault/VaultListPickerScreen.kt`：库列表（名称 + 「上次打开」+ 创建日期）+ 顶栏/底部「新建库」+ 空态
- [x] 解锁页显示「即将解锁「库名」」

## V3 库管理

- [x] 行尾菜单：重命名（对话框）、删除（确认对话框，提示不可恢复）
- [x] 删除当前已解锁的库时先 `AutoLockController.lockNow()`
- [x] 创建页新增「库名」输入（默认「我的密码库」），创建失败自动回滚索引
- [x] 解锁成功写入 `lastVaultId`
- [x] 设置页新增「切换密码库」（回库列表）

## V4 文档

- [x] `design.md` 记录索引只存显示名、旧库原地保留、单会话限制
- [x] 追加决策记录（#72 索引与旧库处理、#73 单会话与切换语义）

## 验证（AI 在带窗口模拟器冒烟）

- [x] 全新安装 → 创建页（库名默认「我的密码库」）
- [x] 创建库 `work` → 进入空库；索引 `vaults.xml` 有记录，文件落在 `files/vaults/<uuid>.db`
- [x] 杀进程重开 → 直接进该库解锁页，副标题「即将解锁「work」」
- [x] 设置 → 切换密码库 → 库列表显示 `work`
- [x] 再建库 `personal` → 库列表 2 条，`personal` 标「上次打开」
- [x] 冒烟中修掉：新建库报 `IO (-14)`（`filesDir/vaults/` 不存在），改为 `prepareFile` 先 `mkdirs`
- [ ] 重命名 / 删除库（留给真机）
- [ ] 两库条目隔离（路径不同必然隔离，未实测跨库）
- [ ] 旧 `vault.db` 自动注册（需手工放入旧库文件）
- [x] `:app:assembleDebug` + `:app:testDebugUnitTest` + `:app:lintDebug` 通过；单测 7 组 46 例

## V5 归档

- [x] 更新 `SPEC.md`：新增「多密码库」；`密码库数据层` 与「导航与返回栈」同步路径与路由
- [x] `sele close --dry-run` 确认全部 `appended` 后执行 `sele close`
