# 20261024-m3e-alignment 任务清单

原则：先升级工具链拿到公开的 Expressive API，再建统一设计系统，最后逐页改造；每个阶段结束 `assembleDebug` + `lintDebug` 必须通过。

## N1 工具链与依赖升级

- [x] 安装 `platforms;android-37.0`、`build-tools;37.0.0`（必要时 `platforms;android-37.1`）
- [x] Gradle wrapper 8.14.3 → 9.8.x
- [x] AGP 8.13.0 → 9.4.x，Kotlin 2.2.20 → 2.4.x（compose 插件同步）
- [x] `compileSdk`/`targetSdk` → 37，`minSdk` 保持 29
- [x] Compose BOM → 2026.09.00，`material3` 显式固定 `1.5.0-beta02`
- [x] navigation-compose → 2.10.2、activity-compose → 1.13.0、lifecycle → 2.11.0、core-ktx → 1.19.1
- [x] `assembleDebug` + `lintDebug` 通过；JNI 产物仍可打包（16 KB 对齐不变）

## N2 图标替换为 Material Symbols Rounded

- [x] 引入 `MaterialSymbolsRounded` 可变字体到 `res/font/`
- [x] `ui/components/SymbolIcon.kt`：按名称取字素、size/tint 参数化
- [x] 删除 `ui/icons/ShioIcons.kt` 与全部 `Icons.Filled.*` 调用点
- [x] 覆盖图标：close、check、edit、person、key、link、notes、title、lock、settings、search、add、delete、visibility、visibility_off、arrow_back、more_vert

## N3 设计系统（主题层）

- [x] `Shape.kt`：Expressive 阶梯 8/12/16/20/28 + `ListStackShapes`（28 外 / 8 内）
- [x] `Type.kt`：Roboto 显式声明 + `*Emphasized` 风格的取用封装
- [x] `Theme.kt`：改用 `MaterialExpressiveTheme`，`MotionScheme.expressive()`，系统栏与动态取色保持
- [x] `Color.kt`：5 套色板补齐 expressive 所需槽位（surfaceContainer 族、inverse、surfaceBright/Dim）
- [x] 编辑器内一次性紫色 scheme 移除，统一走全局主题
- [x] 全局禁止页面内 hex 颜色（lint 自查）

## N4 通用组件

- [x] `StackedList`：3dp 间距、外 28 / 内 8、首中尾形状
- [x] `FieldItem`：72dp、40dp primaryContainer 圆形图标位、headline/supporting、trailing 图标
- [x] `PressScale`：ripple + 按下缩放，动效取主题 motionScheme
- [x] `EmptyState`：图标 + 标题 + 说明 + 动作

## N5 密码库列表页

- [x] 顶栏（滚动转 surfaceContainer）+ 搜索栏 56dp
- [x] 堆叠列表 + `ExtendedFloatingActionButton`（滚动收起为 FAB）
- [x] 空库 / 搜索无结果两套 M3E 空态
- [x] 列表项 72dp：40dp 圆形首字母/图标、标题、副标题

## N6 条目详情页

- [x] 分组卡片（outside 28dp）+ `ListItem` 行
- [x] 密码显隐用 `ToggleButton`，复制用 `SymbolIcon` 图标按钮 + Snackbar
- [x] 删除走 `DropdownMenu`（error 色 + 图标）+ 确认对话框 28dp
- [x] 空值统一「未设置」，无网址时整行不渲染

## N7 编辑器

- [x] 沿用堆叠列表 + 单字段 sheet，改全局主题
- [x] `ToggleButton` 密码显隐 + `ButtonGroup`（取消 / 保存）
- [x] 校验：行内标红 + Snackbar；放弃修改确认
- [x] sheet 内 `TextField` 使用 M3E 形状与动效

## N8 创建 / 解锁页

- [x] 大标题 + 单字段 filled `TextField` + 全宽主按钮
- [x] 创建页：主口令 + 确认 + 不可恢复提示
- [x] 解锁页：内联错误区（`INVALID_KEY (-3)`）+ 载入 `LoadingIndicator`
- [x] 主口令长度校验（≥ 8）

## N9 设置页与外观页

- [x] 设置：分组 `ListItem` + `Switch`/`Select`（自动锁定）+ 关于区 + Demo 入口
- [x] 外观：`ToggleButton` 组选择主题模式与色板，预览卡用 M3E 形状
- [x] 选择即时生效并持久化（沿用 `AppearancePreferences`）

## N10 原型 Demo 与收尾

- [x] Demo 页外包 M3E 容器（顶栏返回 + 主题一致），内部逻辑不动
- [x] 全局审计：无硬编码色、无旧图标、可点元素均有 ripple + 按压缩放
- [x] `assembleDebug` + `lintDebug` + 模拟器（API 37）/ 真机（Android 16）冒烟
- [x] 审计清单（页面 × 检查项）写入 `handover.md`
- [x] `sele lint` 通过

## 完成定义

- [x] 所有页面在模拟器与真机上外观一致、无回退到默认 M3 的组件
- [x] `MaterialExpressiveTheme` 为唯一根主题，动效走 `MotionScheme.expressive()`
- [x] 图标全部来自 Material Symbols Rounded
- [x] 每个 N 阶段结束都有构建与 lint 记录
- [x] 后续任何界面改动都必须先满足 `.design/changes/20261024-m3e-alignment/design.md` 的 M3E 基线

## N11 反馈修正（真机验证）

- [x] 详情页 / 设置页改为 `StackedList`（3dp 间距、外 28 / 内 8），去掉单一大卡片 + 分隔线
- [x] 密码显隐改为 `IconToggleButton`（48dp，宽度正常）
- [x] 图标光学居中：移除字体图标，改依赖 `material-icons-extended`，`AppIcons` 枚举 + `AppIcon` 统一渲染
- [x] 编辑器删除底部「取消 / 保存」，保存改为顶栏右上角勾选图标
- [x] 全部验证只在真机（vivo V2359A / Android 16）执行，不再启动模拟器
 - [x] release 开启 `isMinifyEnabled` + `isShrinkResources`，把 APK 从 52 MB 压回 25 MB 量级，并在真机确认图标与 Compose 未被误裁

## N12 键盘遮挡修正

- [x] 编辑器单字段 sheet：`imePadding` 从内容层移到 `ModalBottomSheet` 的 modifier，内容改为可滚动
- [x] 真机验证：打开 sheet 后等 7 秒，标题、输入框、「完成」按钮仍完整位于键盘上方

## N13 输入框对齐 M3E

- [x] 编辑器单字段 sheet 的 `TextField` 改为 M3E 形态：填充式容器、16dp 顶部圆角、聚焦 primary 指示线、前置字段图标、占位文案、`bodyLarge` 文本
- [x] 真机验证：用户名 sheet 显示人像图标 + 「登录名或邮箱」，键盘弹出后完整可见
