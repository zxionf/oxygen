# Spec

<!-- 当前设计真相源：一个概念一节，只写现状。历史见 git，取舍理由见 DECISIONS.log。 -->

## 项目定位
- shio-android：shio 内核的 Android 应用端，仓库位于 `oxygen`。
- 内核（加密持久化、同步）由 `hydrogen` 仓库提供；本仓库负责 Android 应用层、内核集成与交互。
- **设计风格固定为 Material 3 Expressive（M3E）**，细节见 `## M3E 设计基线`；任何新页面与界面改动都必须先满足该基线，不再回到默认 M3。

## 技术栈
- Kotlin 2.4.21、Jetpack Compose（BOM 2026.09.00，material3 1.5.0-beta02）、Material 3 Expressive、Lifecycle 2.11.0、Navigation Compose 2.10.2；单 Activity、单 `app` 模块。
- Gradle 9.8.2（wrapper + sha256）、AGP 9.4.1（内置 Kotlin 支持，不应用 `org.jetbrains.kotlin.android`）、JDK 21。
- `compileSdk` / `targetSdk` 37，`minSdk` 29，`applicationId` 为 `top.zxionf.shio`（debug 构建加 `.debug` 后缀）。
- ABI 仅 `arm64-v8a`、`x86_64`；NDK `29.0.14206865`；CMake `3.22.1+`（使用系统 4.4.4）。
- JNI 薄层使用 C++17，静态 C++ 运行时，关闭 C++ 异常与 RTTI。
- 图标使用 `androidx.compose.material:material-icons-extended`（BOM 管版本），经 `ui/components/Icons.kt` 的 `AppIcons`/`AppIcon` 统一取用；不使用字体图标与自绘路径。
- 不引入 DI 框架与序列化库；JSON 使用系统 `org.json`；内存/偏好层不引入 Room/DataStore（数据走内核 `vault.db`）。
- 构建注意：Gradle 9 + JDK 21 需在 `gradle.properties` 的 `org.gradle.jvmargs` 加 `-Djdk.lang.Process.launchMechanism=fork`，否则 CMake 配置阶段报 spawn helper 错误。

## 约束
- **设计风格：全应用固定 Material 3 Expressive**。所有界面必须满足 `## M3E 设计基线`：`MaterialExpressiveTheme` 根主题、Expressive 形状阶梯、Roboto + emphasized 字体、`MotionScheme` 动效、`material-icons-extended` 图标、仅用 scheme role 配色、可点元素 ripple + 按下缩放。新增页面或改动若违反基线，视为未完成。
- 输入类控件统一使用 `ui/components/AppTextField`；列表分组统一使用 `StackedList`/`FieldItem`。
- 内核以 hydrogen C ABI v1（`crates/shio-ffi/include/shio.h`）为准，JNI 启动时校验 `shio_abi_version == 1`。
- `jniLibs` 下的 `libshio_ffi.so` 入库，来源固定 hydrogen 提交 `50e073b3998c690811414aa9534393b4033cac35`；两份产物均满足 16 KB LOAD 对齐。
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

## 数据模型
- 命名空间固定 `demo`，集合固定 `notes`。
- 记录值固定为 `{"text":"<用户输入>"}`，单一文本字段。
- 记录 ID 使用 UUIDv7：`insert` 由内核生成，`batch` 由客户端按相同布局生成；Kotlin 负责 16 字节与标准 UUID 字符串互转。
- `list` 只返回元信息，Demo 取回列表后对每项调用 `get` 取正文，单次最多 50 条。
- 数据库路径为应用私有目录 `filesDir/shio-demo.db`；界面显示完整路径。

## 演示界面
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
│   ├── kernel/
│   │   ├── NativeShio.kt        # external 声明与库加载
│   │   ├── ShioDatabase.kt      # 句柄、线程与 UUID
│   │   └── ShioException.kt     # code + message
│   ├── demo/
│   │   ├── DemoScreen.kt        # Compose 单页
│   │   └── DemoViewModel.kt     # 状态与操作编排
│   └── ui/theme/
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

不引入 DI 框架，不拆多模块，不引入导航库。JSON 解析使用系统自带 `org.json`，避免为 Demo 增加序列化依赖。

## 目标与范围
首个变更只验证一件事：Android 应用能通过 C ABI 与 JNI 薄层完整驱动 shio 内核。

- 覆盖：建库/开库、单条写入、读取、删除、列表、批量写、统计、关闭与重开。
- 不覆盖：多端同步、改口令、数据密钥轮换、只读模式界面、发布签名、CI、任何业务功能。
- 界面只求可操作与可观察，不做视觉打磨与导航结构。

## JNI 接口
句柄对 Kotlin 表现为不透明的 `Long`，不是裸指针。JNI 内部维护 `id -> Handle` 表，`Handle` 持有 `shio_kernel*` 与互斥量；`destroy` 先从表中摘除，再等待在途调用结束并释放。

| Kotlin 声明 | C ABI | 说明 |
|---|---|---|
| `abiVersion(): Int` | `shio_abi_version` | 启动时校验为 1 |
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
| `lastError(h): String` | `shio_last_error` | 异常消息已包含该内容，保留接口便于排查 |

错误处理：任一调用返回非 `SHIO_OK` 时，JNI 读取 `shio_last_error` 并抛出 `ShioException(code, message)`；Kotlin 侧按 `code` 展示。所有由内核分配的输出缓冲区在 JNI 内复制后立即 `shio_free`。

## 安全与线程
- 口令只在 `String -> ByteArray` 转换后传入 native，调用结束立即清零字节数组；JVM 字符串无法清零，Demo 接受该限制。
- 口令、明文、记录正文不写日志。
- 所有 native 调用在 `Dispatchers.IO.limitedParallelism(1)` 上串行执行，避免句柄生命周期竞态；JNI 层仍用句柄表与互斥量兜底。
- Compose 只消费 `StateFlow`，不在主线程直接调用 native。

## 构建与产物
- `compileSdk 36`、`minSdk 29`、`targetSdk 36`，ABI 为 `arm64-v8a` 与 `x86_64`，NDK `29.0.14206865`，CMake `3.22.1+`（使用系统 4.4.4）。
- 构建使用 JDK 21（系统 JDK 或 Android Studio JBR）；AGP 与 Gradle 版本在脚手架阶段选定并固定到 Version Catalog / wrapper。
- 首版 `libshio_ffi.so` 直接取自 hydrogen 提交 `50e073b`；Linux 用 `scripts/sync-shio.sh`、Windows 用 `scripts/sync-shio.ps1` 通过 `cargo-ndk` 重建并复制，来源提交与 NDK 版本写入脚本常量；Linux 脚本同时同步 `include/shio.h`。
- Debug 构建即可，不做签名与混淆配置。

## M3E 目标与原则
把整个应用对齐 Material 3 Expressive（下称 M3E），并把 M3E 作为长期约束：后续任何界面改动都必须满足本文件定义的基线。范围包含工具链、依赖、设计系统（形状/字体/动效/图标/组件）与全部现有界面。

参考基准：`https://lnkiai.github.io/m3e-canvas/agent.md`（M3E Canvas 的设计稿规范，给出 M3E 的组件清单、尺寸、形状阶梯、字体与动效语义）。

## M3E 设计基线
### 设计系统

- **主题**：全局使用 `MaterialExpressiveTheme`（material3 ^1.5），不再用 `MaterialTheme` 直接包裹根内容。
- **形状**：Expressive 圆角阶梯——列表/卡片 20dp 及以上、列表堆叠外角 28dp / 内角 8dp、对话框 28dp、按钮为胶囊。禁止在页面里写死非阶梯圆角（除 8/20/28 这类阶梯值）。
- **字体**：Roboto（Roboto Flex 变量字体作为可选未来项），标题使用 `*Emphasized` 变体（`headlineLargeEmphasized` 等）建立 Expressive 的强调层级。
- **动效**：全局 `MotionScheme.expressive()`；按下/展开/转场统一取主题的 motion scheme，不在页面里手写弹簧参数。
- **图标**：Material Symbols Rounded（本地字体资源 + `Icon` 包装），不再混用 Material Icons 与自绘路径作为最终方案。
- **颜色**：只用 scheme role（`primary`、`surfaceContainerLow`、`onSurfaceVariant`…）。页面内不得出现 hex 颜色（色板定义集中在 `ui/theme/`）。
- **组件**：优先使用 M3E 新增组件——`ButtonGroup`、`FloatingToolbar`、`SplitButton`、`ToggleButton`、`LoadingIndicator`、`MaterialShapes`、wave 进度；列表用 3dp 间距的堆叠列表（外角 28 / 内角 8）。
- **尺寸基线**：顶栏 64dp（大标题型 112/152dp）、列表项 72dp、按钮/FAB 56dp、图标按钮 48dp、搜索栏 56dp、底部导航 104dp、开关行 48dp、对话框 28dp 圆角。
- **交互**：所有可点元素 ripple + 按下缩放；返回统一走反向转场；系统返回与手势返回行为一致。

### 当前实现的审计结论（改动前）

| 项 | 现状 | 与 M3E 的差距 |
|---|---|---|
| 主题 | `MaterialTheme` + 手写 5 套浅/深 ColorScheme | 无 expressive motion、无 emphasized 字体、无 Expressive 形状默认值 |
| 形状 | 默认 M3 阶梯（4/8/12/16/28） | 未采用 Expressive 阶梯；列表无 28/8 堆叠处理 |
| 字体 | 默认 Typography | 未使用 `*Emphasized` |
| 动效 | 页面内手写弹簧（编辑器） | 未走主题 motion scheme |
| 图标 | 核心 Material Icons + 8 个本地自绘 | 不是 Symbols Rounded |
| 主题色适用范围 | 全局可配置色板；编辑器内单独紫色 light scheme | 视觉不一致（编辑器为一次性方案） |
| 页面 | 列表/详情/编辑器已 M3 化；创建/解锁/设置/外观仍是占位页或半成品 | 未覆盖全应用；占位页无 M3E 结构 |
| 组件 | ListItem/Surface/Button 为主 | 未使用 ButtonGroup / FloatingToolbar / SplitButton / ToggleButton / LoadingIndicator |

## 工具链与依赖升级
- Gradle wrapper 8.14.3 → 9.8.x；AGP 8.13.0 → 9.4.x（material3 1.5 要求 AGP ≥ 9.1、compileSdk 37）。
- Kotlin 2.2.20 → 2.4.x（含 compose 编译器插件同步）。
- `compileSdk`/`targetSdk` 36 → 37（安装 `platforms;android-37.0`、`build-tools;37.0.0`）。
- Compose BOM → 2026.09.00（Compose 1.12.1），`material3` 显式覆盖到 1.5.0-beta02 以获得公开的 Expressive API。
- navigation-compose 2.9.8 → 2.10.2；activity-compose → 1.13.0；lifecycle → 2.11.0；core-ktx → 1.19.1。
- minSdk 保持 29（用户设备 Android 16、模拟器 Android 17；不为旧系统做妥协，但也不无理由抬高 minSdk）。
- 新增 `MaterialSymbolsRounded` 可变字体资源（`res/font/`），提供 `SymbolIcon` 包装；不引入 `material-icons-extended`。

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
2. **条目详情**：分组卡片（`ListItem` 行、外 28dp）+ 复制/显隐用 `SymbolIcon` 图标按钮 + 删除走 `DropdownMenu`（error 色）+ 底部 `ButtonGroup`?（复制/编辑主次操作）。
3. **编辑器**：沿用叠列表 + 字段 sheet，去掉一次性紫色主题，改用全局主题与 `ToggleButton`（密码显隐）+ `ButtonGroup`（取消/保存）。
4. **创建/解锁**：M3E 大标题 + 单字段 `TextField`（filled）+ 全宽主按钮 + 内联错误区；解锁错误码仍显示 `INVALID_KEY (-3)`。
5. **设置**：`ListItem` 分组 + `Switch`/`Select`（自动锁定）+ 载入 `LoadingIndicator`（可选）+ 关于区。
6. **外观**：色板/主题选择改为 `ToggleButton` 组 + 预览卡（沿用现有逻辑，替换视觉）。
7. **原型 Demo**：只做包裹（顶栏返回 + M3E 容器），内部逻辑不变。

## 验收
- 每个阶段 `assembleDebug` + `lintDebug` 通过。
- 全局审计项：无页面内硬编码色、无自绘图标（除 Symbols Rounded 字体不可用时的临时回退）、可点元素有 ripple + 按下缩放、形状取自阶梯。
- 模拟器（API 37）与真机（Android 16）各做一轮主流程冒烟；原型 Demo 可打开。
- 交付一份 M3E 审计清单（页面 × 检查项）写入 `handover.md`。

## 与现有变更的关系
- `20261023-password-vault` 的 M7–M20 不取消；本变更先完成设计系统对齐与现有页面改造，之后密码库继续按原任务清单推进（新页面直接按 M3E 实现）。
- 数据层仍按 M11 计划进行；本变更不改数据行为，只改表现层与工具链。

## 表单输入框规范

- 组件：全应用输入统一走 `ui/components/AppTextField.kt`（编辑器字段 sheet、创建/解锁页共用），页面不再各自写 TextField。

- 形态：标准的 M3 **线框式** `OutlinedTextField`。未聚焦描边 1dp `outline`，聚焦 2dp `primary`；容器色为 `Color.Transparent`，即背景与所在容器（sheet 的 `surfaceContainerLow`）一致，不再单独填充；不画额外下划线。
- 标签：浮动标签抬到描边线上（`OutlinedTextField` 原生的缺口效果），空且未聚焦时居中作为占位，不再另加标题。
- 辅助文本：`bodySmall` + `onSurfaceVariant`，放在输入框下方（不放进 `supportingText`，避免被外框包住）。
- 图标：**不使用前置图标**；密码字段仅保留尾部 `IconToggleButton` 切换显隐。
- 键盘：字段编辑使用 `ModalBottomSheet`，`Modifier.imePadding()` 加在 sheet 的 `modifier` 上，内容可滚动，键盘弹出后不被遮挡。
