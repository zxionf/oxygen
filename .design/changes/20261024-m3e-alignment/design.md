# 20261024-m3e-alignment

> 后续修正：本文件写于 M3E 初期。N11–N13 已将图标改为 `material-icons-extended`（决策 #34），编辑器保存移到顶栏，输入框统一为 `AppTextField`，外观页保留色卡 + 圆形色板。以下涉及 Material Symbols Rounded、`ButtonGroup`、filled `TextField` 的描述属于历史方案，当前基线以 `.design/SPEC.md` 为准。

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
├── SymbolIcon.kt       Material Symbols Rounded 包装
├── StackedList.kt      3dp 间距、外 28/内 8 的列表容器
├── FieldItem.kt        72dp 字段行（图标 40dp circle + headline/supporting + 编辑）
└── PressScale.kt       ripple + 按下缩放的统一修饰符
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
