# 20261023-password-vault 任务清单

原则：每个阶段只做一个小功能；阶段结束必须 `:app:assembleDebug` 通过并截图确认；M1–M10 只用假数据，不碰 `vault.db`；原型 Demo 每次都要能打开。

> 2026-10-24 对齐：M8/M9 的页面已在 `20261024-m3e-alignment` 中提前完成；M10 仅部分完成；M7 与 M11–M20 仍未开始。

## M0 文档

- [x] 创建变更 `20261023-password-vault`
- [x] 写 `design.md`：范围、导航、页面、主题、数据、安全、阶段拆分
- [x] 写本任务清单，拆到可逐项验收
- [x] 追加导航、自动锁定、主题、MVP 范围的决策
- [x] 更新 `STATE.md` 与 `阶段计划.md`

## M1 导航依赖与骨架

- [x] `gradle/libs.versions.toml` 增加 `navigation-compose` 版本与 library
- [x] `app/build.gradle.kts` 增加依赖
- [x] 新增 `app/` 包：`Routes` 常量与 `AppNavHost`
- [x] 为 `gate/create/unlock/vault/entry/editor/settings/appearance/demo` 建占位页（demo 直接复用 `DemoScreen`，见决策 #23）
- [x] 启动入口改为 `gate`，暂时固定进入 `vault` 占位页
- [x] 构建通过并截图占位页

## M2 主题状态与配色方案

- [x] 新增 `AppearancePreferences`：`ThemeMode`（SYSTEM/LIGHT/DARK）与 `Palette`
- [x] 用 `SharedPreferences` 读写，默认跟随系统 + Shio 蓝
- [x] `ShioTheme` 支持 mode/palette，补齐浅色与深色 `ColorScheme`
- [x] 预设色板：Shio 蓝、青、绿、紫、橙
- [x] API 31+ 支持动态取色，29–30 回退 Shio 蓝
- [x] 状态栏/导航栏图标颜色跟随深浅色
- [x] 构建并在浅色、深色下截图

## M3 外观页 UI

- [x] 顶部迷你列表预览卡
- [x] “主题”区：跟随系统 / 浅色 / 深色 三张预览卡
- [x] “颜色”区：动态取色 + 5 个预设色板
- [x] 动态取色在 API < 31 上显示不可用提示（改为能力探测：API 31+ 且系统资源可解析；不可用时仍显示并置灰，见决策 #25）
- [x] 选择后立即生效并持久化
- [x] 截图：浅色、深色、动态取色

## M4 密码库列表 UI（假数据）

- [x] 假条目模型与 `VaultViewModel` 假状态
- [x] `Scaffold` + TopAppBar（锁定、设置）+ 搜索框 + FAB
- [x] 列表项布局：图标/首字母、标题、用户名或网址
- [x] 空库与搜索无结果两种空状态
- [x] 搜索匹配标题、用户名、网址、备注
- [x] 截图列表与空状态

## M5 条目详情 UI（假数据）

- [x] 字段行布局：用户名、密码、网址、备注（改为 M3 分组列表：ListItem + 备注独立区块，见决策 #27）
- [x] 密码默认隐藏，眼睛按钮切换，等宽字体（图标改用自建 `ShioIcons.Visibility` / `VisibilityOff`）
- [x] 复制按钮 + Snackbar 提示（假复制）
- [x] 删除确认对话框
- [x] 截图隐藏/显示两种状态

## M6 编辑器 UI（假数据）

- [x] 新建/编辑共用一个页面，字段布局（顶栏保存 + 标题字段项 + 4 项堆叠列表，见决策 #29）
- [x] 标题与密码必填校验（行内标红 + Snackbar）
- [x] “放弃修改？”返回确认（✕ 与系统返回共用）
- [x] 保存后回退并刷新来源页面（假数据：`FakeVaultStore` + StateFlow，见决策 #28）
- [x] 截图新建与校验错误（后续改为不在设备上验证，仅构建 + lint）

## M7 密码生成器

- [x] 底部弹层：长度滑块 12–64（默认 20）
- [x] 大写/小写/数字/符号开关与排除易混淆字符
- [x] `SecureRandom` 生成，保证每个勾选字符集至少一个（`PasswordGeneratorTest` 7 例）
- [x] “使用此密码”写回编辑器
- [x] 真机验证弹层（长度/开关/写回），截图未入仓

## M8 设置页 UI

- [x] 自动锁定单选：立即 / 1 分钟 / 5 分钟 / 15 分钟（仅内存，持久化见 M16）
- [x] “外观”入口
- [x] “原型 Demo”入口（已接 `DemoScreen`，见决策 #23）
- [x] “关于”：应用版本、内核版本（`shio_version()`）、ABI 版本（`shio_abi_version()`）、内核提交、设备架构、系统、数据库路径
- [x] 构建并真机验证设置页（M3E N9/N10；截图未入仓）

## M9 创建 / 解锁 UI（假数据）

- [x] 创建页：主口令 + 确认 + 不可恢复提示
- [x] 解锁页：主口令 + 内联错误区
- [x] 假 gate：按假标志进入创建或解锁（当前固定进入 `vault`，真实分流见 M11）
- [x] 主口令长度校验（至少 8 字符）
- [x] 构建并真机验证创建/解锁页（M3E N8/N10；截图未入仓）

## M10 导航与返回栈打磨

- [x] 解锁/创建成功清空 `unlock`/`create` 回退栈
- [x] 锁定清空回退栈并进入 `unlock`（`popUpTo(graph.id) { inclusive = true }`；栈底隐藏返回箭头，见决策 #46）
- [x] 编辑器未保存返回确认
- [x] 旋转屏幕后表单与选中项保留（真机四项全部通过）
- [x] 列表：搜索词与过滤结果保留
- [x] 编辑器：字段内容、当前编辑的 sheet 保留
- [x] 设置：自动锁定选择保留；外观：选中色板保留
- [x] 解锁页：输入与显隐状态保留
- [x] “原型 Demo”接到现有 `DemoScreen`（M1 已提前接好，见决策 #23）
- [x] 构建并验证 Demo 可打开（M3E N10 / 真机）

## M11 数据模型与仓库

- [x] `VaultEntry` 数据类与 JSON 编解码（`VaultEntryCodec`，`org.json`）
- [x] `VaultRepository`：create / unlock / lock 封装（含 `entries`/`insert`/`update`/`delete` 门面）
- [x] 创建显式 `{"create":true}`，解锁显式 `{"create":false}`
- [x] gate 改为按 `vault.db` 是否存在分流（`VaultPaths.exists`）
- [x] 映射 `shio_sync_enable` / `shio_sync_info`（JNI + `ShioDatabase.enableSync/syncInfo`）
- [x] 建库后立即启用同步并校验 `sync_info.db_id` 与 `format_version == 2`，失败则删库不留半成品
- [x] 构建通过（`assembleDebug` / `assembleRelease` / `lintDebug` / `testDebugUnitTest`；release APK 内含两个 sync 符号）

## M12 真实创建 / 解锁 / 锁定

- [x] 创建页接入仓库（`create` 内含 `sync_enable` 校验），成功后进入列表，`format=2`/`db_id` 诊断显示在设置→关于（debug）
- [x] 解锁页接入仓库，错误口令显示 `INVALID_KEY (-3)`（按错误码分支给文案）
- [x] 锁定调用 `destroy`，清空内存状态并进入解锁页（`VaultComponent.repository.lock()`）
- [x] 构建并在真机完成 create → lock → unlock 冒烟（含错误口令 INVALID_KEY）

## M13 真实列表与搜索

- [x] 解锁后 `list` + 逐条 `get`（每页 100、上限 2000）
- [x] 内存搜索：标题、用户名、网址、备注（`VaultEntrySearchTest` 8 例；密码不参与搜索）
- [x] 写入后刷新：列表页 `LifecycleResumeEffect` 触发 `refresh()` 重读整库
- [x] 按标题排序（`title.lowercase()` 升序）
- [x] 真机验证真实数据列表（解锁后从 vault.db 读 8 条样例、搜索命中/无结果、排序；截图未入仓）

## M14 真实详情

- [x] 按 ID 读取详情（`VaultRepository.entry`，`get` 不存在返回 null → 「条目不存在」）
- [x] 复制用户名/密码（`ClipData` + API 33+ `EXTRA_IS_SENSITIVE`）
- [x] 仅 `http/https` 网址可外部打开（`vault/VaultUrl` + `VaultUrlTest` 3 例；真机弹系统跳转确认）
- [x] 删除真实调用 `delete` 并确认（AlertDialog → `repository.delete` → 回退）
- [x] 验证删除后列表刷新（真机删除「邮箱」后列表剩 7 条）

## M15 真实编辑器

- [x] 新建调用 `insert`（`EntryEditorViewModel.save`）
- [x] 编辑调用 `put`
- [x] 校验与未保存确认（标题/密码必填 + 「放弃修改？」）
- [x] 保存后列表/详情刷新（都靠 resume 重读）
- [x] 验证新增与编辑各一条（真机：新增 TE → 改标题 TEE，列表/详情均更新）

## M16 自动锁定偏好与冷启动

- [x] 自动锁定时长写入 `SharedPreferences`（`vault/AutoLockPreferences`，`session/auto_lock_timeout`）
- [x] 冷启动后恢复自动锁定设置（真机 force-stop 后仍显示 5 分钟）
- [x] 验证主题模式与色板冷启动恢复（真机：LIGHT + TEAL，重启后解锁页直接是浅色青色）
- [x] 真机验证重启后的外观与设置（截图未入仓）

## M17 自动锁定

- [x] `onStop`/`onStart` 接入，旋转（`isChangingConfigurations`）不锁定（真机旋转两次仍在列表）
- [x] 默认切后台立即锁定（真机：立即模式按 Home 再回来即在解锁页）
- [x] 1/5/15 分钟按时间戳判断（`AutoLockPolicy` + `AutoLockPolicyTest` 5 例；真机 1 分钟：后台 5s 不锁、65s 锁）
- [x] 锁定清空条目与口令状态，导航到解锁页（`lockNow` → `destroy` + 清会话 → `lockCount` → 清栈进 unlock）
- [x] 验证：后台立即锁、旋转不锁、超时锁（真机全部通过；解锁页按返回键退出应用）

## M18 安全收尾

- [x] release 启用 `FLAG_SECURE`，debug 不启用（真机 release 截图全黑、debug 正常；release 也不含样例数据）
- [x] 审查日志，确保不含口令、条目正文、复制内容（全仓零 `Log.*`/`println`/`printStackTrace`，JNI 亦然）
- [x] 复制带敏感标记（`EXTRA_IS_SENSITIVE`，API 33+；自动清除下一阶段）
- [x] 离开详情/锁定后密码显示状态复位（`remember` 而非 `rememberSaveable`，真机验证返回再进为掩码）
- [x] `assembleDebug` + `assembleRelease` + `lintDebug` + `testDebugUnitTest` 通过

## M19 双设备验收

- [ ] x86_64 模拟器：创建、增删改查、搜索、复制、锁定/解锁、自动锁、旋转
- [ ] arm64 真机：同上一套流程
- [ ] 杀进程后重开：数据仍在，需重新解锁
- [ ] 错误口令返回 `INVALID_KEY`
- [ ] 原型 Demo 仍可打开
- [ ] 截图与结果写入 `handover.md`

## M20 归档

- [ ] 把结论折叠进 `SPEC.md`
- [ ] 更新 `阶段计划.md` 与 `STATE.md`
- [ ] `sele lint` 通过
- [ ] 归档预览后执行 `sele close`

## 阶段完成定义

- [ ] 每个 M 阶段都有一次构建成功与截图
- [ ] M1–M10 不写 `vault.db`，不改现有内核接口
- [ ] M11 起所有数据库调用仍走 `ShioDatabase` 的单线程调度
- [ ] 原型 Demo 在每次阶段验收中都能打开
- [ ] 进入下一阶段前，上一阶段任务全部勾选
