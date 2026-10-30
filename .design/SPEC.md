# Spec

<!-- 当前设计真相源：一个概念一节，只写现状。历史见 git，取舍理由见 DECISIONS.log。 -->

> 密码库 MVP 已归档（变更 `20261023-password-vault`），设计结论就在本文件；任务与验收记录见 `.agent/work/20261023-password-vault/`。

## 项目定位
- shio-android：shio 内核的 Android 应用端，仓库位于 `oxygen`。
- 内核（加密持久化、同步）由 `hydrogen` 仓库提供；本仓库负责 Android 应用层、内核集成与交互。
- **设计风格固定为 Material 3 Expressive（M3E）**，细节见 `## M3E 设计基线`；任何新页面与界面改动都必须先满足该基线，不再回到默认 M3。

## 密码库 MVP 范围
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

- 生物识别、Autofill、TOTP

**多端同步：MVP 不做同步 UI、配对与网络传输，但首次建库按内核要求启用同步**

- 内核 `DECISIONS[40]` 要求 Android 首次真实建库必须调用 `sync_enable` 并校验 `sync_info`，库因此是 `format=2`（不可回退），不采用先建 `format=1` 再升级的路径。
- 密码库仍只建立同步身份：内核已映射 `shio_sync_enable` 与 `shio_sync_info`，建库时调用并校验；变更页、快照、轮换、配对与全部网络能力均未接入（清单见「内核 ABI 对齐」）。

现有 `demo/` 与 `shio-demo.db` 保留，在设置中提供“原型 Demo”入口。

## 技术栈
- Kotlin 2.4.21、Jetpack Compose（BOM 2026.09.00，material3 1.5.0-beta02）、Material 3 Expressive、Lifecycle 2.11.0、Navigation Compose 2.10.2；单 Activity、单 `app` 模块。
- Gradle 9.8.2（wrapper + sha256）、AGP 9.4.1（内置 Kotlin 支持，不应用 `org.jetbrains.kotlin.android`）、JDK 21。
- `compileSdk` / `targetSdk` 37，`minSdk` 29，`applicationId` 为 `top.zxionf.shio`（debug 构建加 `.debug` 后缀）。
- ABI 仅 `arm64-v8a`、`x86_64`；NDK `29.0.14206865`；CMake `3.22.1+`（使用系统 4.4.4）。
- JNI 薄层使用 C++17，静态 C++ 运行时，关闭 C++ 异常与 RTTI。
- 图标使用 `androidx.compose.material:material-icons-extended`（BOM 管版本），经 `ui/components/Icons.kt` 的 `AppIcons`/`AppIcon` 统一取用；不使用字体图标与自绘路径。
- 不引入 DI 框架与序列化库；JSON 使用系统 `org.json`；内存/偏好层不引入 Room/DataStore（数据走内核库文件）。
- 构建注意：Gradle 9 + JDK 21 需在 `gradle.properties` 的 `org.gradle.jvmargs` 加 `-Djdk.lang.Process.launchMechanism=fork`，否则 CMake 配置阶段报 spawn helper 错误。

## 约束
- **设计风格：全应用固定 Material 3 Expressive**。所有界面必须满足 `## M3E 设计基线`：`MaterialExpressiveTheme` 根主题、Expressive 形状阶梯、Roboto + emphasized 字体、`MotionScheme` 动效、`material-icons-extended` 图标、仅用 scheme role 配色、可点元素 ripple + 按下缩放。新增页面或改动若违反基线，视为未完成。
- 输入类控件统一使用 `ui/components/AppTextField`；列表分组统一使用 `StackedList`/`FieldItem`。
- 内核以 hydrogen C ABI v1（`crates/shio-ffi/include/shio.h`）为准，JNI 启动时校验 `shio_abi_version == 1`。
- `jniLibs` 下的 `libshio_ffi.so` 入库，来源固定 hydrogen 提交 `5f3e8e324cda0bf772e21ad7bcd2036435ebcdd4`；两份产物均满足 16 KB LOAD 对齐。
- 所有 native 调用在 `Dispatchers.IO.limitedParallelism(1)` 上串行执行，主线程不调用 native。
- 口令以字节数组传入 native，调用结束立即清零；口令、明文与记录正文不写日志。
- 无权限声明；`allowBackup=false`；release 构建不包含冒烟入口。

## 验证基线
- 日常验证只用真机：vivo V2359A（PD2309M），Android 16（API 36），`arm64-v8a`；`scripts/acceptance.sh` 仍可用于历史回归。
- 模拟器（Android 17 / API 37、16 KB page）仅保留为历史记录，不作为当前验收手段。
- 每个界面阶段至少通过 `:app:assembleDebug` + `:app:lintDebug`；涉及内核的改动另跑 Demo 冒烟。
- release 构建开启 R8（`isMinifyEnabled` + `isShrinkResources`），JNI 入口靠 `proguard-rules.pro` 的 keep 规则保留。

## 内核集成
- 内核以 hydrogen 的 C ABI v1 为准，头文件为 `crates/shio-ffi/include/shio.h`，产物为 `libshio_ffi.so`。
- 本仓库只新增 JNI 薄层 `libshio_jni.so`，通过 CMake 以 `IMPORTED` 目标链接 `jniLibs/<abi>/libshio_ffi.so`，最终两个 `.so` 一起打包。
- 加载顺序：先 `System.loadLibrary("shio_ffi")`，再 `System.loadLibrary("shio_jni")`，避免依赖隐式解析。
- JNI 层不复制任何内核逻辑：只做字符串/字节数组转换、句柄表维护、错误码到异常映射、缓冲区释放与清零。

## 数据模型（原型 Demo）
- 命名空间固定 `demo`，集合固定 `notes`。
- 记录值固定为 `{"text":"<用户输入>"}`，单一文本字段。
- 记录 ID 使用 UUIDv7：`insert` 由内核生成，`batch` 由客户端按相同布局生成；Kotlin 负责 16 字节与标准 UUID 字符串互转。
- `list` 只返回元信息，Demo 取回列表后对每项调用 `get` 取正文，单次最多 50 条。
- 数据库路径为应用私有目录 `filesDir/shio-demo.db`；界面显示完整路径。
- 密码库目标模型为 `vault/entries`，库文件位置见「多密码库」（多库时为 `filesDir/vaults/<uuid>.db`）；详见「密码库数据层」。

## 演示界面（原型 Demo）
单页 Compose，自上而下：

1. 口令输入框与「打开/创建」按钮，打开后显示数据库路径与 ABI 版本。
2. 记录列表：UUID 简写 + 正文预览，点击选中。
3. 输入框 + 「添加」；选中记录时「删除」可用。
4. 「批量写入 3 条」：验证 `batch`。
5. 「关闭」「重新打开」「刷新」「统计」按钮。
6. 状态区：最近一次操作结果；错误时显示错误码与消息。

打开时选项 JSON 固定 `{"create":true}`；关闭后再次操作应显示 `SHIO_CLOSED`；重新打开时 `ShioDatabase.open` 会先销毁旧句柄。

## 工程结构
单 `app` 模块，包名 `top.zxionf.shio`，Gradle Kotlin DSL + Version Catalog。

```
app/src/main/
├── java/top/zxionf/shio/
│   ├── MainActivity.kt
│   ├── app/                     # Routes、AppNavHost、占位页
│   ├── kernel/
│   │   ├── NativeShio.kt        # external 声明与库加载
│   │   ├── ShioDatabase.kt      # 句柄、线程与 UUID
│   │   └── ShioException.kt     # code + message
│   ├── demo/
│   │   ├── DemoScreen.kt        # 原型 Demo 单页
│   │   └── DemoViewModel.kt     # 状态与操作编排
│   ├── vault/                   # VaultEntry/Codec、VaultRepository、VaultViewModel、VaultLibrary、VaultTag、VaultTransfer、VaultUrl、SensitiveClipboard、AutoLock*
│   ├── ui/
│   │   ├── vault/               # 条目列表、库列表（多库）、详情、编辑器
│   │   ├── unlock/              # 创建 / 解锁
│   │   ├── settings/            # 设置、外观、修改主口令、标签管理、备份/导入导出
│   │   ├── components/          # StackedList、FieldItem、AppTextField、Icons
│   │   └── theme/               # Color、Shape、Type、Theme、AppearancePreferences
├── cpp/
│   ├── shio_jni.cpp             # JNI 薄层
│   ├── CMakeLists.txt
│   ├── include/shio.h           # hydrogen C ABI 只读副本
│   └── README.md
└── jniLibs/<abi>/libshio_ffi.so
app/src/debug/                   # SmokeActivity / AcceptanceActivity，仅 debug 变体
scripts/smoke.sh                 # 设备冒烟脚本
scripts/acceptance.sh            # P4 验收入口（LOCKED、进程重启持久化）
scripts/sync-shio.sh             # Linux 内核产物同步（构建 + 头文件 + 对齐校验）
```

不引入 DI 框架，不拆多模块；密码库使用 Navigation Compose（M1 引入），原型 Demo 内部仍是单页。JSON 解析使用系统自带 `org.json`，避免增加序列化依赖。

## 目标与范围（内核集成 Demo）
首个变更只验证一件事：Android 应用能通过 C ABI 与 JNI 薄层完整驱动 shio 内核。

- 覆盖：建库/开库、单条写入、读取、删除、列表、批量写、统计、关闭与重开。
- 不覆盖：多端同步、改口令、数据密钥轮换、只读模式界面、发布签名、CI、任何业务功能。
- 界面只求可操作与可观察，不做视觉打磨与导航结构。
- 密码库 MVP 的当前设计在本文件的「密码库 MVP 范围」及后续各节；本节只描述内核集成 Demo。

## JNI 接口
句柄对 Kotlin 表现为不透明的 `Long`，不是裸指针。JNI 内部维护 `id -> Handle` 表，`Handle` 持有 `shio_kernel*` 与互斥量；`destroy` 先从表中摘除，再等待在途调用结束并释放。

| Kotlin 声明 | C ABI | 说明 |
|---|---|---|
| `abiVersion(): Int` | `shio_abi_version` | 启动时校验为 1 |
| `kernelVersion(): String` | `shio_version` | 内核编译期版本（如 `0.2.0`），无需打开数据库 |
| `open(path: String, password: ByteArray, optionsJson: ByteArray?): Long` | `shio_open` | 失败抛出 `ShioException` |
| `close(handle: Long)` | `shio_close` | 可重复调用 |
| `destroy(handle: Long)` | `shio_kernel_destroy` | 释放句柄，之后调用返回已关闭错误 |
| `insert(h, ns, coll, json): ByteArray` | `shio_insert` | 返回 16 字节 UUID |
| `put(h, ns, coll, id, json): ByteArray` | `shio_put` | 返回元信息 JSON |
| `get(h, ns, coll, id): ByteArray` | `shio_get` | 返回 `{"meta":...,"value":...}` |
| `delete(h, ns, coll, id): Boolean` | `shio_delete` | 返回删除前是否存在 |
| `list(h, ns, coll, after, limit, includeDeleted): ByteArray` | `shio_list` | 返回 `{"items":[...],"next":...}`，items 只有元信息 |
| `batch(h, ns, coll, opsJson): ByteArray` | `shio_batch` | 输入 `[{"id":"uuid","value":...}]`，输出元信息数组 |
| `stats(h): ByteArray` | `shio_stats` | `format_version`、`key_id`、`db_bytes` |
| `changePassword(h, newPassword): Unit` | `shio_change_password` | 只重包裹密钥，不重写记录；旧口令立即失效 |
| `backup(h, outPath): ByteArray` | `shio_backup` | 在线备份到指定路径，返回摘要 JSON |
| `restore(backupPath, targetPath, password, optionsJson): Long` | `shio_restore` | 恢复到新文件，返回新句柄（仅用于校验） |
| `syncEnable(h, deviceId): ByteArray` | `shio_sync_enable` | 首次建库启用同步，返回 `sync_info` |
| `syncInfo(h): ByteArray` | `shio_sync_info` | 同步摘要；未启用返回 `SHIO_SYNC_DISABLED` |
| `lastError(h): String` | `shio_last_error` | 异常消息已包含该内容，保留接口便于排查 |

错误处理：任一调用返回非 `SHIO_OK` 时，JNI 读取 `shio_last_error` 并抛出 `ShioException(code, message)`；Kotlin 侧按 `code` 展示。所有由内核分配的输出缓冲区在 JNI 内复制后立即 `shio_free`。

## 安全与线程
- 口令只在 `String -> ByteArray` 转换后传入 native，调用结束立即清零字节数组；JVM 字符串无法清零，Demo 接受该限制。
- 口令、明文、记录正文不写日志。
- 所有 native 调用在 `Dispatchers.IO.limitedParallelism(1)` 上串行执行，避免句柄生命周期竞态；JNI 层仍用句柄表与互斥量兜底。
- Compose 只消费 `StateFlow`，不在主线程直接调用 native。

## 构建与产物
- `compileSdk` / `targetSdk` 37，`minSdk` 29，ABI 为 `arm64-v8a` 与 `x86_64`，NDK `29.0.14206865`，CMake `3.22.1+`（使用系统 4.4.4）。
- 构建使用 JDK 21（系统 JDK 或 Android Studio JBR）；Gradle 9.8.2、AGP 9.4.1 与 Kotlin 2.4.21 固定到 Version Catalog / wrapper。
- 首版 `libshio_ffi.so` 直接取自 hydrogen 提交 `5f3e8e3`；Linux 用 `scripts/sync-shio.sh`、Windows 用 `scripts/sync-shio.ps1` 通过 `cargo-ndk` 重建并复制，来源提交与 NDK 版本写入脚本常量；Linux 脚本同时同步 `include/shio.h`。
- Debug 构建用于日常验证；release 开启 R8（`isMinifyEnabled` + `isShrinkResources`），JNI 入口由 `proguard-rules.pro` 的 keep 规则保留。当前 release 仍使用 debug keystore，没有正式发布签名。

## M3E 目标与原则
把整个应用对齐 Material 3 Expressive（下称 M3E），并把 M3E 作为长期约束：后续任何界面改动都必须满足本文件定义的基线。范围包含工具链、依赖、设计系统（形状/字体/动效/图标/组件）与全部现有界面。

参考基准：`https://lnkiai.github.io/m3e-canvas/agent.md`（M3E Canvas 的设计稿规范，给出 M3E 的组件清单、尺寸、形状阶梯、字体与动效语义）。

## M3E 设计基线
### 设计系统

- **主题**：全局使用 `MaterialExpressiveTheme`（material3 ^1.5），不再用 `MaterialTheme` 直接包裹根内容。
- **形状**：Expressive 圆角阶梯——列表/卡片 20dp 及以上、列表堆叠外角 28dp / 内角 8dp、对话框 28dp、按钮为胶囊。禁止在页面里写死非阶梯圆角（除 8/20/28 这类阶梯值）。
- **字体**：Roboto（Roboto Flex 变量字体作为可选未来项），标题使用 `*Emphasized` 变体（`headlineLargeEmphasized` 等）建立 Expressive 的强调层级。
- **动效**：全局 `MotionScheme.expressive()`；按下/展开/转场统一取主题的 motion scheme，不在页面里手写弹簧参数。
- **图标**：`androidx.compose.material:material-icons-extended` 的矢量图标，经 `ui/components/Icons.kt` 的 `AppIcons` / `AppIcon` 统一取用；不使用字体图标与自绘路径（见决策 #34）。
- **颜色**：只用 scheme role（`primary`、`surfaceContainerLow`、`onSurfaceVariant`…）。页面内不得出现 hex 颜色（色板定义集中在 `ui/theme/`）。
- **组件**：优先使用 M3E 新增组件——`ButtonGroup`、`FloatingToolbar`、`SplitButton`、`ToggleButton`、`LoadingIndicator`、`MaterialShapes`、wave 进度；列表用 3dp 间距的堆叠列表（外角 28 / 内角 8）。
- **尺寸基线**：顶栏 64dp（大标题型 112/152dp）、列表项 72dp、按钮/FAB 56dp、图标按钮 48dp、搜索栏 56dp、底部导航 104dp、开关行 48dp、对话框 28dp 圆角。
- **交互**：所有可点元素 ripple + 按下缩放；返回统一走反向转场；系统返回与手势返回行为一致。

### M3E 改造前审计

| 项 | 现状 | 与 M3E 的差距 |
|---|---|---|
| 主题 | `MaterialTheme` + 手写 5 套浅/深 ColorScheme | 无 expressive motion、无 emphasized 字体、无 Expressive 形状默认值 |
| 形状 | 默认 M3 阶梯（4/8/12/16/28） | 未采用 Expressive 阶梯；列表无 28/8 堆叠处理 |
| 字体 | 默认 Typography | 未使用 `*Emphasized` |
| 动效 | 页面内手写弹簧（编辑器） | 未走主题 motion scheme |
| 图标 | 核心 Material Icons + 8 个本地自绘 | 未统一到 `AppIcons` / `AppIcon` |
| 主题色适用范围 | 全局可配置色板；编辑器内单独紫色 light scheme | 视觉不一致（编辑器为一次性方案） |
| 页面 | 列表/详情/编辑器已 M3 化；创建/解锁/设置/外观仍是占位页或半成品 | 未覆盖全应用；占位页无 M3E 结构 |
| 组件 | ListItem/Surface/Button 为主 | 未统一到 StackedList / FieldItem / AppTextField |

## 工具链与依赖升级
- Gradle wrapper 8.14.3 → 9.8.x；AGP 8.13.0 → 9.4.x（material3 1.5 要求 AGP ≥ 9.1、compileSdk 37）。
- Kotlin 2.2.20 → 2.4.x（含 compose 编译器插件同步）。
- `compileSdk`/`targetSdk` 36 → 37（安装 `platforms;android-37.0`、`build-tools;37.0.0`）。
- Compose BOM → 2026.09.00（Compose 1.12.1），`material3` 显式覆盖到 1.5.0-beta02 以获得公开的 Expressive API。
- navigation-compose 2.9.8 → 2.10.2；activity-compose → 1.13.0；lifecycle → 2.11.0；core-ktx → 1.19.1。
- minSdk 保持 29（用户设备 Android 16、模拟器 Android 17；不为旧系统做妥协，但也不无理由抬高 minSdk）。
- 图标采用 `material-icons-extended` 矢量图标（见决策 #34）；不使用 `MaterialSymbolsRounded` 字体与 `SymbolIcon` 包装。

## 设计系统落地
```text
ui/theme/
├── Color.kt            5 套色板的浅/深 scheme（补齐 surfaceContainer 族与 inverse）
├── Shape.kt            ExpressiveShapes：8/12/16/20/28 + 列表堆叠形状
├── Type.kt             Roboto + 全局 emphasized 变体映射
├── Motion.kt           MotionScheme 取用封装（expressive）
├── Theme.kt            MaterialExpressiveTheme + 系统栏 + 动态取色
└── AppearancePreferences.kt   外观偏好（沿用）
ui/components/
├── Icons.kt            AppIcons 枚举 + AppIcon（material-icons-extended 矢量图标）
├── StackedList.kt      3dp 间距、外 28/内 8 的列表容器
├── FieldItem.kt        72dp 字段行（图标 40dp circle + headline/supporting + 编辑）
├── PressScale.kt       ripple + 按下缩放的统一修饰符
├── EmptyState.kt       M3E 空态（图标 + 标题 + 说明 + 动作）
└── AppTextField.kt     统一输入框（线框式 + 浮动标签 + 框外辅助文本）
```

## 页面改造清单
1. **密码库列表**：`TopAppBar`（可滚动转 `surfaceContainer`）+ 搜索栏 56dp + 堆叠列表 + `ExtendedFloatingActionButton`（滚动收起为 FAB）；空态与无结果用 M3E 空态（图标 + 标题 + 说明 + 动作按钮）。
2. **条目详情**：堆叠列表（`ListItem` 行、外 28dp）+ 复制/显隐用 `IconButton` / `IconToggleButton` + `http/https` 网址提供「在浏览器打开」+ 删除走 `DropdownMenu`（error 色）并二次确认。
3. **编辑器**：堆叠字段列表 + 字段 sheet，使用全局主题、`IconToggleButton`（密码显隐），保存放顶栏右上角勾选图标。
4. **创建/解锁**：M3E 大标题 + `AppTextField`（线框式）+ 全宽主按钮 + 内联错误区；解锁错误码仍显示 `INVALID_KEY (-3)`。
5. **设置**：`StackedList` 分组 + 自动锁定单选对话框 + 外观 / 原型 Demo 入口 + 关于（应用版本、内核版本、ABI 版本、内核提交、设备架构、系统、数据库路径）。
6. **外观**：主题预览卡 + 色卡 / 圆形色板（`ToggleButton` 无法展示颜色本身，见决策 #32）。
7. **原型 Demo**：只做包裹（顶栏返回 + M3E 容器），内部逻辑不变。

## 验收
- 每个阶段 `assembleDebug` + `lintDebug` 通过。
- 全局审计项：无页面内硬编码色、无自绘图标、可点元素有 ripple + 按下缩放、形状取自阶梯。
- 日常验证以真机为准（vivo V2359A / Android 16）；模拟器（API 37 / 16 KB）保留为历史回归记录；原型 Demo 必须可打开。
- 交付一份 M3E 审计清单（页面 × 检查项）写入 `handover.md`。

## 与密码库变更的关系
- 密码库 MVP 已随变更 `20261023-password-vault` 归档：它的设计结论已折叠进本文件（本项目定位、密码库 MVP 范围、导航与返回栈、密码库数据层、密码生成器、偏好持久化、自动锁定、安全措施等节）。
- 变更目录仅留作历史记录，不再从那里取设计上下文；任务与验收记录在 `.agent/work/20261023-password-vault/`。

## 表单输入框规范
- 组件：全应用输入统一走 `ui/components/AppTextField.kt`（编辑器字段 sheet、创建/解锁页共用），页面不再各自写 TextField。

- 形态：标准的 M3 **线框式** `OutlinedTextField`。未聚焦描边 1dp `outline`，聚焦 2dp `primary`；容器色为 `Color.Transparent`，即背景与所在容器（sheet 的 `surfaceContainerLow`）一致，不再单独填充；不画额外下划线。
- 标签：浮动标签抬到描边线上（`OutlinedTextField` 原生的缺口效果），空且未聚焦时居中作为占位，不再另加标题。
- 辅助文本：`bodySmall` + `onSurfaceVariant`，放在输入框下方（不放进 `supportingText`，避免被外框包住）。
- 图标：**不使用前置图标**；密码字段仅保留尾部 `IconToggleButton` 切换显隐。
- 键盘：字段编辑使用 `ModalBottomSheet`，`Modifier.imePadding()` 加在 sheet 的 `modifier` 上，内容可滚动，键盘弹出后不被遮挡。

## 密码生成器
- 实现：`vault/PasswordGenerator.kt`（纯 Kotlin，`SecureRandom`）+ `ui/vault/PasswordGeneratorPanel.kt`（UI）。
- 规则：长度 12–64（默认 20，滑块步进 1）；大写/小写/数字/符号四组开关，可排除易混淆字符（`I l 1 O 0 o | S 5 s B 8 b 2 Z z`）；先为每个勾选的字符集各取一个字符，其余随机填充，最后 Fisher–Yates 打乱，避免前几位固定。
- 符号集固定为 `!#$%&*+-=?@^_`（不含引号、括号、反斜杠等易在别处出问题的字符）。
- 交互：入口在编辑器的「密码」sheet 内（「生成密码」文字按钮），生成器就地替换 sheet 内容，不叠加第二个 bottom sheet；选项一变立即重新生成；「使用此密码」把结果写回编辑器字段并回到手动输入态，不直接落库。
- 展示：结果放在 `surfaceContainerHigh` 卡片（`titleLarge`，最多 2 行省略），右上角「重新生成」；未勾选任何字符集时按钮禁用并提示「至少选择一种字符集」。
- 单测：`app/src/test/java/top/zxionf/shio/vault/PasswordGeneratorTest.kt`（长度、字符集完整、排除易混淆、空选择、随机性，7 项）。

## 设置页「关于」信息
- 数据来源：内核版本与 ABI 版本来自**运行时**查询（`kernel/KernelInfo.kt` 调 `shio_version()` / `shio_abi_version()`）；应用版本与 `SHIO_KERNEL_COMMIT` / `SHIO_ABI_VERSION` 由 Gradle 写入 `BuildConfig`（`app/build.gradle.kts` 的 `defaultConfig`）；设备架构与系统版本取 `android.os.Build`。
- 展示行（`StackedList` + `ListItem`，外 28dp / 内 8dp）：版本 `0.1.1-debug（2）`、内核版本（当前 `0.2.0`）、ABI 版本、内核提交 `hydrogen @ <提交>`（release 12 位短哈希，debug 完整哈希）、设备架构 `arm64-v8a`、系统 `Android 16（API 36）`、数据库路径。
- ABI 版本取值：native 加载成功且与构建约定一致时显示单个数字；不一致显示 `<运行时>（期望 <构建值>）`；库缺失或加载失败时内核版本与 ABI 版本都显示「不可用」（页面不崩溃）。
- 内核提交只做产物溯源，不参与版本判断；`scripts/sync-shio.sh` / `sync-shio.ps1` 在同步产物时会把 `BuildConfig.SHIO_KERNEL_COMMIT` 自动改写为脚本里的 `PINNED_COMMIT`，手改无效也不必要。
- 诊断入口：**长按「版本」行**弹出「诊断信息」对话框（`extraLarge` + `StackedList`），debug 与 release 都可用，展示存储格式 `format=2`、同步身份 `db_id`、设备身份 `device_id`、内核版本、ABI 版本与数据库路径，并可「复制」（写入剪贴板时打 `EXTRA_IS_SENSITIVE`）。未解锁（无会话）时长按不触发。
- debug 构建另在「关于」列表里直接多出「存储格式」「同步身份」两行；release 列表保持 7 行，内部标识只走长按诊断。
- 实现：给「版本」行加 `Modifier.pointerInput { detectTapGestures(onLongPress = …) }`（不用 `combinedClickable`，免额外 opt-in）；弹窗数据与 debug 两行同源（`VaultSession` + `KernelInfo`），复制复用 `ClipData` + `EXTRA_IS_SENSITIVE`，不新增第二套剪贴板代码。

## 导航与返回栈
单 Activity、字符串路由，参数经 `Uri.encode`（不引入 kotlinx-serialization）；使用 `androidx.navigation:navigation-compose`。

| 路由 | 页面 | 说明 |
|---|---|---|
| `gate` | 启动分流 | 无库去 `create`，一个库直接去 `unlock`，多个库去 `vaults` |
| `create` | 创建密码库 | 库名 + 主口令 + 确认；成功后注册索引 |
| `vaults` | 密码库列表 | 选择/新建/重命名/删除库；不需解锁 |
| `unlock?vaultId={vaultId}` | 解锁 | 指定库的主口令；参数缺省回退索引第一条 |
| `vault` | 密码库列表 | 搜索、条目列表、FAB、锁定、设置 |
| `entry/{id}` | 条目详情 | 查看、复制、显隐、外链、编辑、删除 |
| `editor?entryId={entryId}` | 编辑器 | 无 `entryId` 为新建 |
| `settings` | 设置 | 自动锁定、外观、Demo、关于 |
| `appearance` | 外观 | 主题模式与配色 |
| `demo` | 原型 Demo | 复用现有 `DemoScreen` |

- 解锁/创建成功：`navigate(vault) { popUpTo(graph.id) { inclusive = true } }`，`vault` 成为新栈底（多库下不必区分来自 `unlock` 还是 `create`）。
- 锁定：`navigate(unlock) { popUpTo(graph.id) { inclusive = true }; launchSingleTop = true }`，**整个回退栈被清空**，解锁页成为唯一目的地；因此系统返回键在解锁页直接退出应用，不能退回已锁定的列表/详情/设置。
- 创建/解锁页在无上一级时（`previousBackStackEntry == null`）不显示返回箭头：`LockScreen(onBack = null)`，避免出现点了没反应的死按钮。
- 详情 → 编辑保存后回退一级；删除后 `popBackStack()` 回列表；编辑器有未保存修改时返回先弹「放弃修改？」。
- 旋转：列表搜索词在 `VaultViewModel`（ViewModel 跨配置变更存活）、编辑/创建/解锁表单与生成器参数用 `rememberSaveable`、外观选择写 `SharedPreferences`；详情页密码显隐用 `remember`，旋转即复位为隐藏。

## 内核 ABI 对齐
对照基线：hydrogen `5f3e8e3`（`shio 0.2.0`，`SHIO_ABI_VERSION = 1`）。`app/src/main/cpp/include/shio.h` 与内核同名文件逐字节一致（由 `scripts/sync-shio.sh` 同步并校验）。

已映射的符号（`NativeShio` / `shio_jni.cpp`）：`shio_abi_version`、`shio_version`、`shio_open`、`shio_close`、`shio_kernel_destroy`、`shio_insert`、`shio_put`、`shio_get`、`shio_delete`、`shio_list`、`shio_batch`、`shio_stats`、`shio_change_password`、`shio_backup`、`shio_restore`、`shio_sync_enable`、`shio_sync_info`、`shio_last_error`、`shio_free`（JNI 内部调用）。

**有意不映射**的符号及原因：

| 符号 | 原因 |
|---|---|
| `shio_rotate_data_key` | MVP 不做密钥轮换（内核已支持，留待后续变更） |
| `shio_flush` | 目前所有写入以 `shio_batch`/单条写为单位提交，`close`/`destroy` 前无需额外屏障；后续需显式落盘再补 |
| 其余同步符号（`sync_changes`/`apply*`/`offers`/`snapshot*`/`rotation`/`migrate*`/`pairing*`、`shio_open_with_pairing`） | 本阶段只做「建库时启用同步」，不做同步 UI、配对与网络传输；**后续同步变更再接** |

错误码：`ShioException` 覆盖 `shio.h` 的全部 23 个 `SHIO_*` 常量（`0` 与 `-1`…`-23`），新增错误码需同步补常量。

运行环境相关事实（来自内核文档，应用侧需遵守）：

- `.so` 按 API 24 基线构建（`.note.android.ident` = 24），应用 `minSdk = 29` 可正常加载；不要求两者一致。
- Android 上 redb 的字节范围锁不可用，内核在数据库旁维护 `<db>.lock` 并用 `flock` 加锁：写句柄独占、只读句柄共享，冲突返回 `SHIO_LOCKED (-8)`。**因此同一进程不要同时持有两个写句柄**（`ShioDatabase.open` 会先销毁旧句柄），也不要整体删除/搬走数据库目录而不关闭句柄；锁文件可重建，不必清理。
- 记录格式：`format=1` 为普通库，`format=2` 为启用同步后的库（不可退回）；`data_version` 由调用方解释，内核不强制。**密码库首次建库后即 `format=2`**（见下节）。
- `shio_open` 的 `options_json` 只接受 `create`、`read_only`、`max_value_size`、`cache_size`，未知字段会被拒绝；`create` 与 `read_only` 不能同时为真。

## 密码库数据层
- 代码：`vault/VaultLibrary.kt`（多库索引与文件定位）、`vault/VaultRepository.kt`（建库/解锁/锁定 + `vault/entries` 读写）、`vault/VaultEntryCodec.kt`（`org.json` 编解码）。
- 集合：命名空间 `vault`、集合 `entries`（条目）与 `tags`（标签）；记录值只放业务字段，ID 与时间戳取内核 meta；条目通过 `tagIds` 引用标签，详见「标签与收藏」。

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

- `type` 暂固定为 `login`（分类 UI 属于下一阶段）；`VaultEntryCodec` 只编解码 `title`/`username`/`password`/`url`/`notes` 五个字段。
- 建库顺序（内核 `.design/api.md` + DECISIONS[40]）：`open(create=true)` → `sync_enable` → 校验 `sync_info.db_id` 非空且 `stats().format_version == 2`；任一步失败就 `destroy` 并删除 `vault.db` 与 `vault.db.lock`，不留 format=1 半成品。
- 解锁：显式 `{"create":false}`；`sync_info` 取不到（`SHIO_SYNC_DISABLED`，即 format=1 旧库）时仍允许解锁，只是没有同步身份。
- 锁定：`ShioDatabase.destroy()` 并清空 `VaultSession`；界面层沿用「锁定 → `unlock`，清空回退栈」路径。
- 列表：`list`（每页 100、上限 2000）逐条 `get` 取正文，内存中搜索。
- 线程：仓库只暴露 `suspend` 函数，内部全部经 `ShioDatabase` 的单线程 IO 调度器；同一实例重复开库前先 `destroy`，避免 Android `<db>.lock` 的单写约束。
- 同步范围：其余同步符号（变更页、快照、轮换、配对）与同步 UI、网络传输不在本阶段，这里只建立同步身份。

### 会话与界面接入

- `vault/VaultComponent.kt`：进程级单例持有唯一的 `VaultRepository`。解锁状态必须跨 Activity 配置变更与导航回退存活（旋转不能把人踢回未解锁），所以不放 ViewModel；锁定（`lock()`）即销毁句柄并清空会话。
- `LockScreen` 直接接仓库：创建走 `create(path, password)`（内部 `sync_enable` + `format=2` 校验），解锁走 `unlock(path, password)`；成功后清空输入状态并回调 `onSuccess(session)`。
- 失败文案按 `ShioException.code` 分支并带上常量名与码值，例如口令错误显示 `主口令不正确（INVALID_KEY (-3)）`；`INVALID_ARGUMENT` 表示库已存在、`LOCKED` 表示被其它进程占用、`BUSY` 表示轮换未完成、`CORRUPTED`/`UNSUPPORTED_FORMAT` 单独提示。
- 锁定：`VaultComponent.repository.lock()` → `destroy` → 清空回退栈 → `unlock`。
- `AppNavHost` 的 `RequireUnlocked` 守卫：进程被回收后系统恢复导航栈时，受保护页面（`vault`/`entry`/`editor`/`settings`/`appearance`）若发现没有会话，就按库索引重定向（优先 `lastVaultId`，否则单一库或库列表）并清栈，避免无解锁直接看到内容。
- 设置 → 关于在 debug 且已解锁时多两行诊断：`存储格式 format=2` 与 `同步身份 <db_id>`；release 不展示。

### 列表与搜索（真实数据）

- `VaultViewModel` 以 `VaultComponent.repository` 为数据源：`refresh()` 调用 `repository.entries()`（`list` 每页 100、上限 2000，逐条 `get` 取正文），结果放内存后做搜索与排序。
- 搜索字段：标题、用户名、网址、备注，忽略大小写；**密码不参与搜索**（避免通过搜索探测口令）。排序按 `title.lowercase()` 升序。
- 刷新时机：列表页 `LifecycleResumeEffect` 每次重新可见都 `refresh()`，覆盖「保存/删除后返回」「旋转」「从后台回前台」；不做增量合并。
- 界面状态：`VaultUiState` 增加 `isLoading`/`errorMessage`。首次加载且无数据时整页 `LoadingIndicator`；出错用 `EmptyState`（图标 + 文案 + 「重试」）；空库与无搜索结果沿用两种空态。
- debug 样例：解锁后若库为空且本进程未播种过，把 `SampleVaultEntries` 逐条 `insert` 进真实库（`VaultComponent.samplesSeeded` 保证只播一次）；release 不播种。

### 详情页（真实数据）

- `ui/vault/EntryDetailViewModel`：`bind(id)` 后按 ID 读单条（`VaultRepository.entry` → `get`），`NOT_FOUND` 视为「条目不存在」；`LifecycleResumeEffect` 在回到前台/旋转时重读，编辑保存返回即见新值。
- 复制：`ClipData.newPlainText` 写入系统剪贴板，API 33+ 加 `ClipDescription.EXTRA_IS_SENSITIVE`，避免剪贴板浮层与输入法联想展示明文；复制成功用 Snackbar 提示。
- 外链：`vault/VaultUrl.isHttpUrl` 只放行 `http`/`https` 且带主机名的地址（`javascript:`/`file:`/`intent:`/无 scheme 一律拒绝），通过 `ACTION_VIEW` 交给系统；无主机的条目直接不显示「在浏览器打开」按钮。
- 删除：`AlertDialog` 确认 → `VaultRepository.delete`（内核 `delete`）→ 成功后 `onBack()`；列表靠 resume 刷新，不再手动同步内存。

### 编辑器（真实数据）

- `ui/vault/EntryEditorViewModel`：编辑时 `bind` 读原值（`repository.entry`），`save` 时新建走 `insert`、编辑走 `put`；成功后置 `saved`，页面负责 `onBack`。
- 字段状态仍在页面（`rememberSaveable`，旋转不丢）；只有原值加载完成后才组合编辑主体，避免"先按空值渲染再被覆盖"。
- 校验：标题与密码必填（空值行显示「必填」+ Snackbar 提示）；有未保存修改时返回先弹「放弃修改？」。
- 保存后回退一级；列表与详情都靠 resume 重读内核数据，不做本地缓存同步。
- 原值读取失败（条目被删/库已锁）显示错误态（重试 / 返回），不留可编辑的空壳。

## 偏好持久化
- 外观：`ui/theme/AppearancePreferences`（`appearance` 预置），`theme_mode` 与 `palette` 两个字符串枚举名；选择立即生效并落盘。
- 自动锁定：`vault/AutoLockPreferences`（`session` 预置），键 `auto_lock_timeout`，取值来自 `AutoLockTimeout`（`IMMEDIATELY`/`ONE_MINUTE`/`FIVE_MINUTES`/`FIFTEEN_MINUTES`，默认立即）。
- 两者都是「进程内单例 + Compose 快照状态 + `SharedPreferences` 直接读写」，不引入 DataStore；冷启动由构造时读取恢复，设置页直接读快照状态展示。
- 未知/旧版本写入的枚举名会被忽略并回退默认值，避免升级后崩溃。

## 自动锁定
- 入口：`vault/AutoLockController`（单例，唯一的锁定发起方）。`MainActivity.onStop` 调 `onActivityStopped(isChangingConfigurations)`，`onStart` 调 `onActivityStarted`；旋转等配置变更在 `onStop` 直接返回，不触发锁定。
- 判定：`vault/AutoLockPolicy.shouldLockAfterBackground(timeout, backgroundedAt, now)`（纯函数，可单测）。时间源是 `SystemClock.elapsedRealtime` 的差值，不受系统时间调整影响；时间戳倒流按未超时处理。
- 「立即」：`onStop` 当场锁定（真正进后台才触发，因为旋转被排除）。
- 「1/5/15 分钟」：`onStop` 只记时间戳，`onStart` 比较差值，超过阈值才锁定；因此短暂切走（如看一眼通知）不打断使用。
- 锁定动作只有一处：`AutoLockController.lockNow()` → `VaultComponent.repository.lock()`（`destroy` 句柄、清会话）→ `lockCount` 自增；`AppNavHost` 监听 `lockCount` 变化，清空回退栈并导航到 `unlock`。手动锁定（列表页锁图标）同样走 `lockNow()`，不再在导航层重复写一遍销毁逻辑。
- 外部交互豁免：`beginExternalInteraction()` / `endExternalInteraction()` 计数。期间 `onStop` 只记时间戳、不立即锁定，`onStart` 用 `max(用户偏好, 120 秒)` 判定。用于 SAF 选文件（备份/恢复）、后续导入导出等系统界面调用——否则「立即」模式下这些流程一弹出就销毁句柄。

## 修改主口令
- 入口：`设置 → 安全 → 修改主口令`（`Key` 图标，副标题「下次解锁使用新口令」），路由 `change-password`，受 `RequireUnlocked` 守卫。
- 链路：`ChangePasswordScreen` → `VaultRepository.changePassword` → `ShioDatabase.changePassword`（口令转 `ByteArray` 后 `finally` 清零）→ `NativeShio.changePassword` → `shio_change_password`。
- 界面：新口令 + 确认两个线框输入框（带显隐切换），≥8 字符、两次一致；提交中按钮禁用并显示 `LoadingIndicator`；成功 Snackbar「主口令已修改，下次解锁请使用新口令」并返回设置。
- 不要求输入当前口令：会话已解锁即代表本次启动通过内核校验，内核 `change_password` 也只接受新口令；要求旧口令只能用「销毁句柄 → 重开」验证，失败会强迫用户重新解锁，收益低而破坏性大（决策 #64）。
- 错误文案按 `ShioException` 码分支：`BUSY (-7)` 提示密钥轮换未完成、`CLOSED (-5)` 提示重新解锁、`READ_ONLY`/`CORRUPTED`/`IO` 各有对应提示。
- 内核行为：只重包裹密钥，不重写记录；`format=2` 下会重包裹 `keys2` 全部行；条目内容与 `db_id` 不变，旧口令立即失效。

## 安全措施
- 截屏防护：release 构建在 `MainActivity.onCreate` 设置 `WindowManager.LayoutParams.FLAG_SECURE`（真机 `screencap` 为全黑，最近任务预览同被屏蔽）；debug 不设置，便于截图验收。
- 备份：`allowBackup=false` + `data_extraction_rules.xml` 排除 `root`/`file` 域，云备份与换机迁移都不带走数据库。
- 日志：全部代码零 `Log.*` / `printStackTrace` / `println`；JNI 层也不打印。条目正文、口令、复制内容不会进入日志或崩溃信息。
- 剪贴板：统一走 `vault/SensitiveClipboard`，规则见「剪贴板与敏感复制」。
- 密码显隐：详情页用 `remember`（非 `rememberSaveable`），离开页面或旋转立即复位为隐藏；锁定销毁回退栈后详情页整个重建。
- 口令生命周期：`LockScreen` 在 create/unlock 成功后立即清空输入状态；`ShioDatabase.open` 把口令转成 `ByteArray` 后 `finally` 清零；`ShioDatabase.changePassword` 同样清零；JNI 侧在边界内 `zeroize`/`std::fill`。
- 外链：只放行 `http`/`https` 且有主机名的地址（`VaultUrl`），避免 `javascript:`/`file:`/`intent:` 之类被当作可信跳转。

## 剪贴板与敏感复制
- 入口：`vault/SensitiveClipboard`（唯一复制路径），详情页四类字段与设置页诊断信息共用；其他位置不再直接调 `ClipboardManager`。
- 写入：`ClipData.newPlainText` + API 33+ `EXTRA_IS_SENSITIVE`，避免剪贴板浮层与输入法联想展示明文。
- 自动清除：复制后 30 秒（`CLEAR_AFTER_MILLIS`）清除；锁定（手动或自动）立即清除；再次复制重置计时。
- 只清自己的内容：清除前读当前剪贴板，仍是我们写入的那条才动；若已是用户后复制的内容则放弃。
- 可靠性补强：复制时记 `elapsedRealtime`，`MainActivity.onStart` 调 `clearIfExpired()` 补清（进程被冻结时计时协程不会跑完）；`clearPrimaryClip()` 失败保留待清记录以便重试；清除后复读校验，ROM 忽略清除时用空文本 `ClipData` 覆盖。
- 不在切后台时清除：复制 → 切应用 → 粘贴是主用法，30 秒窗口内必须可用。
- 输入法历史：输入法自带的「剪贴板」会把内容另存一份，应用无权删除。首次复制弹一次提示（`consumeImeNotice`，独立 `clipboard` 预置），说明系统剪贴板会被清除、输入法历史需自行关闭，并提供「打开输入法设置」。彻底避开剪贴板需 Autofill 或应用内分享，属后续变更。
- 平台边界：进程被系统杀死后计时消失，剪贴板残留交系统回收；不引入 `AlarmManager`。

## 列表插入后的滚动
- `LazyColumn` 用记录 ID 作 key：新条目插到开头时，Lazy 会保持旧首项为锚点，导致新条目被挤到搜索框上方只露半行。
- 处理：`VaultListScreen` 记录上一次条目数，条目数增加且当前可见位置在前两项内时 `scrollToItem(0)`；用户已向下翻（`firstVisibleItemIndex > 1`）则不打扰当前位置。

## 兼容与迁移
- 全新库文件，无迁移；Demo 的 `shio-demo.db` 与 `demo/` 命名空间不动。
- 应用启动入口从 Demo 改为 `gate`；Demo 通过设置进入。
- 现有 `scripts/smoke.sh` / `acceptance.sh` 继续可用；后续可增加 Vault 专用探针。

## 备份与恢复
单个库文件在设备上只有一份（多库时每个库各自一份），换机、误删应用、刷机都会丢数据；内核提供同设备备份与恢复（`shio_backup` / `shio_restore`，存储维护 S2）。应用侧在设置里提供两个入口，均作用于**当前已解锁的库**，用系统文件选择器（SAF）落到用户选的位置，恢复前必须显式确认并输入备份的主口令。

### 备份流程
- 入口：`设置 → 数据 → 备份密码库`。
- 流程：
  1. 打开 SAF `CreateDocument`（MIME `application/octet-stream`，默认文件名 `shio-backup-<yyyyMMdd-HHmm>.db`）；
  2. 内核把备份写到应用缓存目录的临时文件（`cacheDir/backup-tmp.db`），返回摘要 JSON（`format_version`、`db_bytes`、`table_count`、`row_count`）；
  3. 应用把临时文件流式拷贝到用户选定的 `content://` URI；
  4. 删除临时文件。
- 成功：Snackbar「已备份到 <文件名>」；失败按 `ShioException` 码给文案，临时文件必定清理。
- 备份文件用**原库主口令**打开，不携带明文与密钥；这一点在界面文案里说明（「恢复时需要当时的主口令」）。

### 恢复流程
- 入口：`设置 → 数据 → 从备份恢复`。
- 流程：
  1. SAF `OpenDocument` 选备份文件；
  2. 弹确认对话框：说明会**替换当前全部条目**、且必须输入该备份的主口令；输入框用 `AppTextField`；
  3. 把 URI 流式拷贝到 `cacheDir/restore-tmp.db`；
  4. 调用 `shio_restore(tmp, filesDir/vault-restored.db, password)`：目标文件必须不存在（先删陈旧残留），内核校验口令与结构，成功返回**新句柄**；
  5. 成功后：销毁当前句柄与当前库文件 → 把 `vault-restored.db` 改名为当前库文件名 → 清空 `VaultSession` → 导航回解锁页（用备份的主口令解锁）；
  6. 任一步失败：删除临时文件与 `vault-restored.db`，**当前库文件与句柄保持不变**，用户仍可继续使用。
- 失败文案：`INVALID_KEY (-3)` →「备份的主口令不正确」；`CORRUPTED (-10)` →「备份文件损坏」；`INVALID_ARGUMENT (-1)` →「目标文件已存在」（应用内部已规避）；`IO`/`DATABASE` 单独提示。

### 为什么恢复要「重启会话」
内核的 `shio_restore` 返回的是指向**新文件**的句柄，而恢复后的库口令是备份当时的那个。继续复用旧会话会让「句柄 → 文件」与「口令 → 会话」错位，因此恢复成功后统一：销毁句柄、替换文件、清会话、回到解锁页，由用户用备份口令重新解锁。这样状态永远只有一条真实路径。

### 备份与恢复界面
- 设置页新增分组「数据」，两行：`备份密码库`（副标题「保存到文件，用当前主口令加密」）、`从备份恢复`（副标题「替换当前数据，需要备份的主口令」）。
- 两个入口都在已解锁状态才可达（设置页本身受 `RequireUnlocked` 守卫）。
- 备份提交期间禁用入口并显示加载；恢复对话框在提交中禁用按钮。

### 实现要点
- `shio_jni.cpp`：`NativeShio_backup(handle, outPath)`（返回摘要 JSON，输出缓冲区 `shio_free`）、`NativeShio_restore(backupPath, targetPath, password, optionsJson)`（返回新句柄并登记到句柄表）。
- `NativeShio` / `ShioDatabase`：`backupTo(path): String`、`restoreToVerifiedFile(backupPath, targetPath, password): Long`（返回仅用于校验的句柄）、`destroyHandle(id)`；口令转 `ByteArray` 后清零。
- `VaultRepository`：`backup(outPath)` 直接透传；`restoreInPlace(backupPath, password)` 负责「临时目标 → 校验通过 → 换文件 → 清会话」的整个事务性流程。
- SAF 用 `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument / OpenDocument)`；流拷贝放 `Dispatchers.IO`。
- 外部交互豁免：SAF 会让 `MainActivity.onStop`，在「立即」锁定下会当场销毁句柄，导致备份/恢复必然失败。因此启动前 `AutoLockController.beginExternalInteraction()`、回调里 `endExternalInteraction()`；豁免期间 `onStop` 只记时间戳，`onStart` 用 `max(用户偏好, 120 秒)` 判定（决策 #65）。
- 不改内核、不改存储格式；备份文件与当前库文件同构（含 `format=2`）。

### 边界
- 不做云盘自动上传、不做定时备份。
- 备份文件落在用户选的位置，应用不再持有；文件本身是加密的，但没有口令提示与找回。
- 恢复只支持本应用同格式备份（`format=1/2` 均可，但会替换当前库）。

### 验证
- 备份到本地文件 → 删掉一条记录 → 从备份恢复 → 条目回到备份时的状态，需要输入备份主口令。
- 恢复时故意输错口令 → 报 `INVALID_KEY`，当前库与条目不受影响。
- 恢复后杀进程重开：新库可解锁、`db_id` 与备份时一致（可在「诊断信息」里看）。
- 备份/恢复过程中界面不假死，失败后仍可正常使用。

## 导入导出
密码库此前只能逐条手敲，不能把数据交给别的工具或从别处迁入；本能力提供 CSV/JSON 双向通道，同时 debug 不再向真实库播种样例。

### 导出
- 入口：`设置 → 数据 → 导出为 CSV` / `导出为 JSON`，走 SAF `CreateDocument`。
- 文件内容（两种格式字段一致）：`title`、`username`、`password`、`url`、`notes`。
- CSV：首行表头 `title,username,password,url,notes`；按 RFC 4180 转义（字段含 `,`、`"`、`\r`、`\n` 时整体加双引号，内部 `"` 变成 `""`）；UTF-8 无 BOM；换行 `\n`。
- JSON：`[{"title":…,"username":…,"password":…,"url":…,"notes":…}, …]`，`org.json` 生成，缩进 2 空格便于人工查看。
- 默认文件名：`shio-export-<yyyyMMdd-HHmm>.csv` / `.json`。
- 导出内容含明文密码，界面文案要提示「导出文件包含明文密码，请妥善保管」。

### 导入
- 入口：`设置 → 数据 → 从文件导入`，SAF `OpenDocument`。
- 格式识别：跳过前导空白后，首字符为 `[` 或 `{` 按 JSON 解析，否则按 CSV 解析（第一条非空行视为表头，缺列按空处理）。
- 校验：`title` 与 `password` 必填；缺任一则该条**跳过并计入跳过数**，不影响其它条目。
- 写入：逐条 `insert`（内核生成新 ID），**不查重、不覆盖**已有记录；同一文件内的重复也照新增。
- 结果：Snackbar 或对话框显示「新增 N 条，跳过 M 条」；全部失败时给出首条错误原因。
- 上限：单次最多导入 2000 条（与列表上限一致），超出部分跳过并在结果里说明。

### 为什么只新增不覆盖
覆盖需要判定「哪条算同一条」，而记录 ID 是内核生成的 UUIDv7，换设备/换库后没有稳定标识；按标题覆盖又会把一个密码更新成另一个服务的旧密码，风险更大。只新增配合「导入前先备份（C13 已有）」是更可控的路径；需要去重时的正确做法是后续单独做「合并/去重」变更。

### 设置入口
- 「数据」分组从 2 行扩展为 4 行：`导出为 CSV`、`导出为 JSON`、`从文件导入`、`备份密码库`、`从备份恢复`（共 5 行，按操作风险排序：导入/导出在前，备份/恢复在后）。
- 导入导出的 SAF 启动前调用 `AutoLockController.beginExternalInteraction()`，回调里 `endExternalInteraction()`（与 C13 一致）。
- 操作提交期间禁用入口并显示「正在读取…/正在导出…」。

### 实现要点
- 新增 `vault/VaultTransfer.kt`（纯 Kotlin，无 Android 依赖，便于单测）：
  - `encodeCsv(entries): String` / `decodeCsv(text): ParseResult`
  - `encodeJson(entries): String` / `decodeJson(text): ParseResult`
  - `detectAndDecode(text): ParseResult`（按首字符分流）
  - `ParseResult(entries: List<VaultEntry>, skipped: Int)`
- 新增 `ui/settings/VaultTransferFiles.kt`：SAF 读写、临时文件、上限检查（与 `VaultBackupFiles` 同构）。
- `VaultRepository` 增加 `insertAll(entries): Int`（逐条 insert，返回成功数）。
- SV 移除：`VaultViewModel.seedSamplesOnce`、`VaultComponent.samplesSeeded`、`BuildConfig` 导入（若不再使用）。

### 边界
- 不导出 `id`、时间戳、`type`（导入时一律新建、`type` 固定 `login`）。
- 不解析第三方密码管理器专有格式（1Password/Bitwarden 的 CSV 需要用户先转换列名）。
- 不做导入预览/逐条勾选；不做去重与合并。
- 导出文件不带任何加密，属于用户自行保管的明文副本。

### 验证
- 导出 JSON / CSV 后能看到表头与全部条目，字段顺序正确。
- 清空库（新建库）→ 导入刚导出的 JSON → 条目数量与内容一致。
- 导入含缺 `password` 的行 → 该行跳过，其余正常导入，结果提示跳过数。
- CSV 中含逗号、双引号、换行的备注 → 导出后再导入，内容完全一致（转义正确）。
- debug 全新安装后首次解锁：库为空，不再自动出现 8 条样例。

## 标签与收藏
条目此前只有五个业务字段，条目一多只能靠搜索。本能力提供按主题归类（标签）与快速挑出常用项（收藏）；标签采用**独立集合 + 条目内引用**，重命名只改一处，标签列表可独立枚举与统计。

### 标签存储结构
- 新集合：命名空间 `vault`、集合 `tags`；记录值 `{"name":"工作"}`，标签 ID 由内核 `insert` 生成（UUIDv7）。
- 条目侧：记录值新增 `tagIds: ["<uuid>", …]`（引用标签 ID），以及 `favorite: true|false`。
- 兼容：旧记录没有这两个字段时按「无标签 / 未收藏」处理，不改 `format`、不做迁移；`data_version` 保持 1（纯字段增量）。
- 悬空引用：读取条目时，`tagIds` 里找不到对应标签的一律忽略（标签被删后条目可能残留旧 ID，不视为错误）。

### 为何用引用而非反向索引
- 标签数量远小于条目数量，重命名/改色只改 `tags` 一条记录即可，不必遍历条目。
- 反向索引（标签里存 entryIds）在删除条目时需要同步清理多处，容易漏。
- 代价：删除标签时要遍历条目清掉引用（条目上限 2000，可接受）；即使漏清也只是悬空 ID，读取时会被忽略，不会显示错误标签。

### 收藏
收藏是条目上的布尔字段 `favorite`，不是特殊标签：语义上它属于「这条记录」而非「分类维度」，且列表排序/筛选要单独处理它的优先级。

### 标签管理
- 入口：`设置 → 数据 → 管理标签`（新页面 `tags`）。
- 页面内容：
  - 顶部输入框 + 「新建」按钮（名称去空白、不许重名、最长 24 字符）；
  - 下方 `StackedList` 列出全部标签，每行显示名称与该标签下的条目数；
  - 行操作：重命名（复用同一个底部 sheet）、删除（`AlertDialog` 确认，提示「N 条条目会失去这个标签」）。
- 删除标签：先删除 `tags` 记录，再遍历条目把该 ID 从 `tagIds` 移除（逐条 `put`，失败不回滚，只记录日志级别的结果；残留 ID 在读取时被忽略）。
- 重命名：只 `put` 标签记录，条目无需变动。

### 编辑与展示
- 编辑器：在「备注」之后新增一行「标签」，显示已选标签名（逗号分隔，为空显示「未选择」），点击弹出多选 sheet：
  - sheet 内列出全部标签（`Checkbox` 勾选）；
  - 顶部「新建标签」输入框，可直接创建并选中；
  - 「完成」保存勾选结果到条目 `tagIds`。
- 详情页：在备注之后新增「标签」区块，用 `AssistChip` 逐个展示；无标签时不显示该区块。
- 列表项：副标题右侧显示最多 2 个标签名（超出以 `+N` 表示），收藏项标题前加一个实心星标图标。

### 列表筛选
- 搜索框下方新增一行筛选：`全部` / `收藏` 两个 `FilterChip`，以及全部标签的 `FilterChip`（横向滚动）。
- 选中标签时与该标签同时为真的条目才显示；`收藏` 与标签可叠加。
- 筛选状态只存在内存（`VaultViewModel`），不持久化；锁定时随会话清空。

### 代码位置
- `vault/VaultTag.kt`：`VaultTag(id, name)` + 编解码。
- `vault/VaultEntry.kt`：新增 `tagIds: List<String> = emptyList()`、`favorite: Boolean = false`；`matches()` 不含标签（标签走筛选）。
- `vault/VaultEntryCodec.kt`：编解码 `tagIds`（JSON 数组）与 `favorite`（布尔）。
- `vault/VaultRepository.kt`：新增 `tags()`、`insertTag(name)`、`renameTag(id, name)`、`deleteTag(id)`、`tagUsage(): Map<String, Int>`、`updateFavorite(id, favorite)`、`removeTagFromEntries(tagId)`。
- `ui/settings/TagsScreen.kt`：标签管理页；路由 `tags`，`RequireUnlocked` 保护。
- `ui/vault/EntryEditorScreen.kt`：「标签」字段行 + 多选 sheet。
- `ui/vault/VaultListScreen.kt`：筛选行 + 列表项标签/星标。
- `ui/vault/EntryDetailScreen.kt`：标签区块 + 顶栏收藏开关。

### 范围之外
- 不做标签颜色、排序、嵌套（层级标签）。
- 不做「按标签批量操作」（批量打标签/删除）。
- 不做标签的同步语义（第三波同步要单独考虑 `tags` 集合的冲突合并）。
- 导入导出暂不含标签：CSV/JSON 仍是五个业务字段；标签随条目迁移留到后续变更。

### 检查清单
- 新建标签 → 编辑器里勾选 → 详情显示 → 列表出现标签名。
- 重命名标签后，条目上的显示同步变化（条目 JSON 未改）。
- 删除标签 → 条目不显示该标签；再读该条目不报错（悬空 ID 被忽略）。
- 收藏后列表筛选「收藏」只剩收藏项；与标签筛选叠加正确。
- 标签名重复 / 空白 / 超长时给出提示且不创建。
- 杀进程重开后标签与收藏状态保留。

## 多密码库

之前只有固定的 `filesDir/vault.db`，无法「工作一套、个人一套」分开口令，也没有删除整套库的入口。多密码库提供：多个互相独立的加密库、各自显示名、启动时可选、可重命名与删除。

### 库索引

- 索引存在 `SharedPreferences`（预置名 `vaults`，键 `entries`），值是一个 JSON 数组，每项：
  ```json
  {"id":"<uuid>","name":"工作","fileName":"<uuid>.db","createdAt":1730000000000}
  ```
- 索引**只含显示名与文件名**，不含口令、条目或任何敏感内容；库本身仍由内核加密。
- 索引随每次增删改立即落盘（`SharedPreferences.edit().apply()`）。

### 文件布局
- 每个库一个文件：`filesDir/vaults/<id>.db`，内核的旁路锁是同目录的 `<id>.db.lock`。
- 旧版本的 `filesDir/vault.db` **原地保留**，首次启动时自动注册为一条索引记录：
  `id = "default"`、`name = "默认密码库"`、`fileName = "vault.db"`（不移动文件，避免大文件复制与失败风险）。
- 新建库时 `id` 用 `UUID.randomUUID()`；文件名即 `<id>.db`。

### 启动分流
- 索引为空（且没有旧 `vault.db`）→ `create`（创建第一个库）。
- 索引恰好一条 → 直接进 `unlock?vaultId=<id>`（保持现在的「打开即解锁」体验）。
- 索引多条 → 进 `vaults`（库列表），由用户选一条进入 `unlock?vaultId=<id>`。
- 记住上次打开的库（`SharedPreferences` 键 `lastVaultId`）：解锁成功后写入；仅供库列表高亮与「继续上次」提示，不改变上面的分流规则。

### 页面与路由
| 路由 | 内容 |
|---|---|
| `vaults` | 库列表：每行显示名称、条目数（未解锁不显示）、创建时间；行点击进解锁；右上「新建库」；行尾菜单可重命名/删除 |
| `unlock?vaultId={id}` | 解锁指定库；标题下显示库名；失败时提示该库 |
| `create` | 创建新库：库名（默认「我的密码库」）+ 主口令 + 确认 |
| `vault` 及之后 | 不变（进入的是「当前已解锁的库」） |

- 路由参数 `vaultId` 缺失时回退到「索引第一条」，保证旧调用与深链不崩。
- 库列表页受「无需解锁即可进入」保护：它不读库内容，因此不需要会话；但行尾的条目数需要解锁后才能算，未解锁时该行不显示条目数。

### 库管理
- **重命名**：只改索引里的 `name`，不动文件。
- **删除**：`AlertDialog` 二次确认（提示「库内全部条目将被永久删除，且不可恢复」）；删除 `<id>.db`、`<id>.db.lock` 与该索引记录；若删除的是当前已解锁的库，先锁定（`AutoLockController.lockNow()`）再删。
- **限制**：不允许删除「正在使用且未锁定」的库（先锁定）；允许删到 0 个库，此时下次启动回到创建页。

### 旧库处理
- 旧安装升级后：`vault.db` 自动成为 `default` 库，用户看到的名称是「默认密码库」，可在库列表里重命名。
- 备份/恢复（C13）仍作用于**当前已解锁的库**：备份用当前库路径，恢复替换当前库文件；多库下路径由 `VaultLibrary` 提供。
- 导入导出（C22）与标签（C21）不受影响，都基于当前库。

### 新增文件
- `vault/VaultLibrary.kt`：索引模型 `VaultLibraryEntry(id, name, fileName, createdAt)` + 读写（`list()`、`register()`、`rename()`、`remove()`、`ensureLegacyRegistered()`）+ `fileFor(context, entry)`。
- `ui/vault/VaultListPickerScreen.kt`（库列表页，命名避免与 `VaultListScreen` 混淆）。
- `Routes.Vaults = "vaults"`、`Routes.unlock(vaultId)`、`Routes.VaultIdArg`。

### 不做的事
- 不做跨库移动条目、不做库导出到另一台设备（备份/恢复逐个库做）。
- 不做库密码提示、不做库图标/颜色。
- 不做「同时解锁多个库」：任一时刻只有一个活动会话（内核句柄唯一，`VaultComponent.repository` 单例）。
- 不做旧 `vault.db` 迁移到 `vaults/` 目录（原地保留，减少失败面）。

### 覆盖场景
- 全新安装：直接进创建页；创建后库列表只有一条，下次启动直接进该库解锁页。
- 已有旧 `vault.db` 升级：启动直接进解锁页，库名为「默认密码库」，解锁后条目完整。
- 新建第二个库：库列表出现两条；分别解锁互不影响（A 库条目不出现在 B 库）。
- 重命名库：列表名称变化，解锁页标题同步；条目不受影响。
- 删除库：文件与索引都消失；删除当前库会先锁定并回到库列表/创建页。
- 杀进程重开：库列表与上次解锁的库仍然正确。
