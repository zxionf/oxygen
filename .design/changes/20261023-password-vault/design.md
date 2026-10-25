# 20261023-password-vault

## 目标与范围

从内核集成 Demo 过渡到个人密码库。先搭 UI 框架并确定视觉风格，再接入真实数据。

MVP 覆盖：

- 首次创建密码库、解锁、手动锁定、切后台自动锁定
- 条目列表、搜索
- 新建 / 编辑 / 删除条目
- 字段：标题、用户名、密码、网址、备注
- 密码显示/隐藏、复制、密码生成器
- 设置：自动锁定、外观、原型 Demo、关于
- 安全：release 禁止截屏、敏感信息不写日志

不在本次范围：

- 多密码库
- 分类、标签、收藏（下一阶段）
- 剪贴板自动清除（下一阶段；MVP 只做复制与敏感标记）
- 生物识别、Autofill、TOTP、导入导出
- 改主口令（内核支持，JNI 未暴露）

**多端同步：MVP 不做同步 UI、配对与网络传输，但首次建库按内核要求启用同步**

- 内核 `DECISIONS[40]` 要求 Android 首次真实建库必须调用 `sync_enable` 并校验 `sync_info`，库因此是 `format=2`（不可回退），不采用先建 `format=1` 再升级的路径。
- 因此 M11 只映射 `shio_sync_enable` 与 `shio_sync_info` 两个符号，建库时调用并校验；其余 20 个同步符号、配对文本与全部网络能力留到后续同步变更。
- 现状与待办写在 `SPEC.md` 的「内核 ABI 对齐」与「密码库数据层」两节。

现有 `demo/` 与 `shio-demo.db` 保留，在设置中提供“原型 Demo”入口。

## 实施原则

1. **UI-first**：M1–M10 使用假数据，先把页面、导航、主题与交互定下来；M11 起再接 `ShioDatabase`。
2. **小步交付**：每个阶段只做一个小功能，阶段结束必须 `assembleDebug` + 截图确认，再进入下一阶段。
3. **不提前改数据层**：UI 阶段不碰 `vault.db`；数据层从 M11 开始。
4. **Demo 不回归**：每次构建后确认原型 Demo 仍能打开。
5. **中文优先**：文案先写中文；英文/多语言后续再立变更。

## 导航

引入 `androidx.navigation:navigation-compose`，单 Activity、字符串路由（不引入 kotlinx-serialization 插件）。

| 路由 | 页面 | 说明 |
|---|---|---|
| `gate` | 启动分流 | 检查 vault.db 是否存在；存在去 `unlock`，否则去 `create` |
| `create` | 创建密码库 | 主口令 + 确认 |
| `unlock` | 解锁 | 主口令、错误展示 |
| `vault` | 密码库列表 | 搜索、条目列表、FAB、锁定、设置 |
| `entry/{id}` | 条目详情 | 查看、复制、编辑、删除 |
| `editor?entryId={entryId}` | 编辑器 | 无 `entryId` 为新建 |
| `settings` | 设置 | 自动锁定、外观、Demo、关于 |
| `appearance` | 外观 | 主题模式与配色 |
| `demo` | 原型 Demo | 复用现有 `DemoScreen` |

返回栈规则：

- 解锁/创建成功 → `vault`，并清掉 `unlock`/`create`。
- 锁定 → `unlock`，清空回退栈。
- 详情 → 编辑；保存后回退到详情或列表，列表/详情重新加载。
- 编辑器有未保存修改时，返回先弹确认框。
- `vault` 返回退出应用；`unlock` 返回退出应用。

## 页面与布局

### 设计基线

- Material 3，edge-to-edge；状态栏/导航栏图标颜色随主题切换。
- 页面左右 16dp；解锁/创建页 24dp；区块间距 12dp；卡片圆角 16dp。
- 列表项高度约 72dp；触控目标不小于 48dp。
- 密码字段使用等宽字体，默认隐藏，可切换显示。
- 图标只用 Material 核心图标，避免引入 `material-icons-extended`。
- 反馈统一用 Snackbar；破坏性操作用确认对话框。

### 创建 / 解锁

- 创建页：主口令、确认主口令、创建按钮；提示“忘记主口令将无法恢复数据”。
- 解锁页：主口令、解锁按钮；错误显示在内联错误区（如 `INVALID_KEY (-3)`）。
- 主口令最短 8 个字符（仅长度校验，不做复杂度规则）。

### 密码库列表

- TopAppBar：标题“密码库”，右侧锁定、设置。
- 搜索框固定在列表上方，匹配标题、用户名、网址、备注。
- 列表项：左侧类型图标/首字母，标题为主，用户名或网址为副标题。
- 空库与搜索无结果分别有空状态。
- FAB“新建条目”。
- 按标题排序（后续可加最近更新/收藏）。

### 条目详情

- TopAppBar：返回、编辑、更多（删除）。
- 字段行：用户名、密码、网址、备注；每行提供复制按钮。
- 密码默认隐藏，离开页面或锁定时复位。
- 网址仅允许 `http/https`，用外部浏览器打开。
- 删除需确认。

### 编辑器

- 字段：标题（必填）、用户名、密码（必填）、网址、备注。
- 密码行：显示/隐藏、生成器入口。
- 有未保存修改时返回弹“放弃修改？”。
- 保存成功回退并刷新来源页面。

### 密码生成器

- 底部弹层：长度 12–64（默认 20），大写/小写/数字/符号开关，排除易混淆字符。
- `SecureRandom`；每个勾选字符集至少生成一个字符，再整体打乱。
- “使用此密码”写回编辑器，不直接写入数据库。

### 设置

- 自动锁定：立即 / 1 分钟 / 5 分钟 / 15 分钟；默认立即。
- 外观：进入 `appearance`。
- 剪贴板清除：本阶段只展示说明，自动清除下一阶段实现。
- 原型 Demo：进入现有 Demo 页面。
- 关于：版本、数据库路径、ABI 版本；debug 显示更多信息。

### 外观（模仿 Gboard Theme）

- 顶部预览卡片：用迷你列表项展示当前配色。
- “主题”区：跟随系统 / 浅色 / 深色 三张大预览卡。
- “颜色”区：动态取色 + 预设色板（Shio 蓝、青、绿、紫、橙）。
- 动态取色仅 API 31+ 可用；API 29–30 显示为不可用并回退 Shio 蓝。
- 选择立即生效并持久化到 `SharedPreferences`；不新增 DataStore 依赖。
- 预设色板使用手写的 light/dark `ColorScheme`，不引入取色库。

## 数据模型

数据库：`filesDir/vault.db`；命名空间 `vault`；集合 `entries`。

```json
{
  "type": "login",
  "title": "GitHub",
  "username": "user@example.com",
  "password": "……",
  "url": "https://github.com",
  "notes": ""
}
```

- `type` 预留为 `login`，分类 UI 下一阶段再启用。
- 创建/更新时间与删除标记使用内核 meta。
- 解锁后 `list` + 逐条 `get` 载入内存（上限 2000 条、按 100 条分页），搜索在内存中做。
- 搜索字段：标题、用户名、网址、备注。
- 新建用 `insert`，编辑用 `put`，删除用 `delete`。
- 创建时显式 `{"create":true}`，成功后立即 `sync_enable` 并校验 `format_version == 2`；解锁时显式 `{"create":false}`，避免误建库。
- 编码/解码：`VaultEntryCodec`（`org.json`），记录值只放业务字段，ID 来自内核 meta。

## 会话与安全

- 创建/解锁/锁定由 `VaultRepository` 封装 `ShioDatabase`；所有数据库调用走其单线程 IO 调度器。
- 主口令以 `ByteArray` 传入 native，调用后清零；ViewModel 中的口令字符串在锁定后清空。
- 锁定 = `ShioDatabase.destroy()`，清空内存条目，导航到 `unlock` 并清空回退栈。
- 自动锁定默认“切后台立即锁定”；设置可改为 1/5/15 分钟。旋转屏幕不算切后台。
- release 构建启用 `FLAG_SECURE`；debug 不启用，便于截图验收。
- 剪贴板复制带敏感标记（API 33+）；自动清除下一阶段实现。
- 口令、条目正文、复制内容不写日志、不进崩溃信息。
- 数据库路径与应用包名不变；`allowBackup=false` 继续生效。

## 计划中的代码结构

```text
app/src/main/java/top/zxionf/shio/
├── MainActivity.kt
├── app/                     # ShioApp、NavHost、路由
├── ui/theme/                # ShioTheme、AppearancePreferences、色板
├── vault/                   # VaultViewModel、VaultRepository、VaultEntry、PasswordGenerator
├── ui/unlock/               # 创建/解锁
├── ui/vault/                # 列表、详情、编辑器、生成器
├── ui/settings/             # 设置、外观
├── kernel/                  # 现有
└── demo/                    # 现有，保留
```

`navigation-compose` 加入 Version Catalog 与 `app` 依赖；其余依赖不新增。

## 阶段拆分

详细任务见 `.agent/work/20261023-password-vault/tasks.md`，按 M1–M20 推进：

- **M1–M3**：导航骨架、主题系统、外观页
- **M4–M8**：列表、详情、编辑器、生成器、设置（假数据）
- **M9–M10**：创建/解锁 UI、导航与返回栈打磨
- **M11–M15**：数据模型、真实会话、列表、详情、编辑器
- **M16–M18**：偏好持久化、自动锁定、安全收尾
- **M19–M20**：双设备验收、归档

每个阶段只做一个很小的功能，结束即构建并截图确认。

## 兼容与迁移

- 全新 `vault.db`，无迁移；Demo 的 `shio-demo.db` 与 `demo/` 命名空间不动。
- 应用启动入口从 Demo 改为 `gate`；Demo 通过设置进入。
- 现有 `scripts/smoke.sh` / `acceptance.sh` 继续可用；后续可增加 Vault 专用探针。
