# 20261027-tags-and-favorites 任务清单

原则：实现完成后由 AI 在带窗口模拟器冒烟（用户可观察），真机验收由用户执行。

## T1 数据层

- [x] `vault/VaultTag.kt`：`VaultTag(id, name)` + `VaultTagCodec`（`{"name":...}`）+ `normalizeName` 校验
- [x] `VaultEntry` 新增 `tagIds: List<String>`、`favorite: Boolean`
- [x] `VaultEntryCodec` 编解码 `tagIds`（只接受字符串项、去重、忽略空）与 `favorite`；空值不写入 JSON
- [x] `VaultRepository`：`tags()`、`insertTag`、`renameTag`、`deleteTag`（含清理条目引用）、`tagUsage`、`updateFavorite`
- [x] 单元测试 `VaultTagTest` 8 例：编解码、规范化、超长/控制字符拒绝、旧记录兼容、非法 tagIds 忽略

## T2 标签管理页

- [x] `Routes.Tags = "tags"`，`AppNavHost` 注册并包 `RequireUnlocked`
- [x] `ui/settings/TagsScreen.kt`：新建输入框 + 列表（名称 + 条目数）+ 重命名对话框 + 删除确认
- [x] 设置页「数据」分组新增「管理标签」行

## T3 编辑器

- [x] 「备注」后新增「标签」字段行（显示已选名称，空则「未选择」；备注行形状改为 middle）
- [x] 多选 sheet：`Checkbox` 勾选 + 顶部新建输入框（新建后自动选中）+ 「完成」
- [x] 保存时写入 `tagIds`，并保留原有 `favorite`

## T4 详情与列表

- [x] 详情页：标签 `AssistChip` 区块（空则不显示）+ 顶栏收藏开关（实心/空心星）
- [x] 列表项：标题前实心星标（收藏）、副标题后最多 2 个标签名（超出 `+N`）
- [x] `VaultViewModel` 筛选状态：`VaultFilterState(onlyFavorite, selectedTagIds, allTags)`，多标签为「同时包含」
- [x] 搜索框下方 `FilterRow`：收藏 chip + 标签 chips + 生效时「清除」
- [x] 标签被删后自动清掉筛选里残留的 ID（避免列表恒空且无 chip 可点）

## T5 文档

- [x] `design.md` 记录引用式存储、悬空引用处理、收藏为字段的理由
- [x] 追加决策记录（#70 引用式标签、#71 收藏为字段）

## 验证（AI 在带窗口模拟器冒烟）

- [x] 新建标签 work → 列表「0 条条目」，Snackbar 提示
- [x] 编辑器勾选 work → 保存 → 列表项显示 `work` 标签
- [x] 详情页显示「标签 / work」区块
- [x] 详情顶栏收藏 → 列表项出现实心星标，`content-desc` 变「取消收藏」
- [x] 点「收藏」筛选 → 出现「清除」按钮，列表仍只显示收藏项
- [ ] 多条目下标签筛选与收藏叠加的过滤效果（模拟器只建了 1 条，留给真机验收）
- [ ] 重命名标签后条目显示同步、删除标签后条目不再显示（同上，真机验收）
- [x] `:app:assembleDebug` + `:app:testDebugUnitTest` + `:app:lintDebug` 通过；单测 6 组 40 例

## T6 归档

- [x] 更新 `SPEC.md`：新增「标签与收藏」；`密码库数据层` 补 `tags` 集合；`导入导出` 边界说明暂不含标签
- [x] `sele close --dry-run` 确认全部 `appended` 后执行 `sele close`
