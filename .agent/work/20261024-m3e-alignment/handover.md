# 20261024-m3e-alignment 交接

## 完成范围

- **N1**：SDK 37（platform/build-tools）安装；Gradle 9.8.2、AGP 9.4.1、Kotlin 2.4.21（AGP 内置 Kotlin，移除 kotlin-android 插件）；compileSdk/targetSdk 37；Compose BOM 2026.09.00 + material3 1.5.0-beta02；navigation 2.10.2、activity 1.13.0、lifecycle 2.11.0、core-ktx 1.19.1。
- **N2**：Material Symbols Rounded 子集字体（32 字形，7.4 KB）+ `SymbolIcon`；移除 `material-icons-core` 与自绘 `ShioIcons`。
- **N3**：`MaterialExpressiveTheme` 根主题、`ShioShapes`（8/12/16/20/28 + 列表 28/8）、`ShioTypography`（Roboto）、`ListStackShapes`；编辑器专属紫色主题删除，动效统一取 `MaterialTheme.motionScheme`。
- **N4**：`StackedList`、`FieldItem`、`rememberPressScale`、`EmptyState`。
- **N5**：列表页（滚动转 surfaceContainer 顶栏、56dp 胶囊搜索、堆叠列表、滚动收起扩展 FAB、两套空态）。
- **N6**：详情页（28dp 分组卡、ListItem 行、ToggleButton 显隐、复制 Snackbar、删除菜单 + 28dp 确认框）。
- **N7**：编辑器（堆叠字段、sheet 单字段编辑、ToggleButton 显隐、底部胶囊 取消/保存）。
- **N8**：创建 / 解锁页（大标题、filled 输入、内联错误、LoadingIndicator、≥8 字符校验）。
- **N9**：设置页（分组列表、自动锁定单选对话框、外观/Demo 入口、版本与数据库路径）。
- **N10**：Demo 增加返回入口；模拟器（API 37）与真机（vivo V2359A / Android 16）主流程冒烟通过；`assembleDebug` + `lintDebug` 通过。

## 审计清单（页面 × 检查项）

| 页面 | 无硬编码色 | Symbols Rounded | 堆叠列表 28/8 | 可点 ripple + 按压缩放 | MotionScheme | 备注 |
|---|---|---|---|---|---|---|
| 密码库列表 | ✅ | ✅ | ✅ | ✅ | ✅（FAB/内容动画） | 搜索无结果/空库两套空态 |
| 条目详情 | ✅ | ✅ | —（单卡 28dp） | ✅（复制/显隐为 IconButton/ToggleButton，带 ripple） | ✅ | 无网址时整行不渲染 |
| 编辑器 | ✅ | ✅ | ✅ | ✅ | ✅ | 底部两枚胶囊按钮 |
| 创建 / 解锁 | ✅ | ✅ | — | ✅ | ✅ | M9 前为假 gate |
| 设置 | ✅ | ✅ | —（分组卡） | ✅ | ✅ | 自动锁定尚未持久化 |
| 外观 | ✅ | ✅ | — | ✅ | ✅ | 色板选择保留色卡+色板 |
| 原型 Demo | ✅（复用 M3 组件） | ✅（返回图标） | — | 部分（Demo 内部为旧按钮） | 部分 | 内部逻辑未改 |

## 已知限制 / 后续

1. **持久化**：仍是 `FakeVaultStore`（内存），release 不带样例数据；M11–M15 接内核 `vault.db`。
2. **自动锁定**：设置页选择只在内存，M16/M17 做偏好持久化与 onStop 生效。
3. **原型 Demo 内部**：仍是早期 M3 单页控件，只加了返回与主题；如需完全 M3E 需要单独改造。
4. **material3 1.5.0-beta02**：beta 版本，升级需重跑双设备冒烟。
5. **签名**：release APK 仍用 debug keystore（无正式密钥）。

## N11 反馈修正（真机验证，2026-10-24）

| 反馈 | 处理 |
|---|---|
| 详情页 / 设置页要用堆叠列表 | 两页都改为 `StackedList`（3dp 间距、外 28dp / 内 8dp），删除单一大卡片与组内分隔线 |
| 圆形按钮图标偏下（首页右上两个、返回箭头等） | 放弃字体图标，改 `material-icons-extended` 的矢量图标；`SymbolIcon` 的 `includeFontPadding=false` 方案随之删除 |
| 详情页 / 解锁页显示密码按钮太宽 | 改用 `IconToggleButton`（48dp，带 checked 语义），编辑器 sheet 同步替换 |
| 编辑器底部两颗按钮不要 | 删除底部「取消 / 保存」，顶栏右上角放勾选图标（`AppIcons.Check`，描述「保存」） |
| 不要再启动模拟器 | 全部验证改在真机 `10AE4N38SA000LK`（vivo V2359A / Android 16）完成 |

### 图标方案

- 依赖：`androidx.compose.material:material-icons-extended`（Compose BOM 1.7.8）
- 入口：`ui/components/Icons.kt` — `AppIcons` 枚举（24 个业务名 → 官方 `ImageVector`）+ `AppIcon`（统一 size/tint/语义）
- 已移除：`res/font/material_symbols_rounded.ttf`、字体图标包装、`ui/icons/` 自绘路径

### 体积影响（未开 R8）

| 构建 | 之前 | 现在 |
|---|---|---|
| debug APK | ~34 MB | 69 MB |
| release APK | 27 MB | 52 MB |

待办：release 打开 `isMinifyEnabled` + `isShrinkResources` 后复测体积与真机运行。

## N12 R8 压缩与键盘遮挡（真机验证）

### release R8

- `isMinifyEnabled = true` + `isShrinkResources = true`
- `proguard-rules.pro` 保留 `top.zxionf.shio.kernel.NativeShio` 与所有 native 方法名（JNI 走 `Java_<pkg>_<class>_<method>` 名字查找）
- 结果：release **52 MB → 7.8 MB**（debug 仍 69 MB，未开 R8）
- 真机验证：图标、列表/设置/外观/编辑器界面正常；Demo 页打开 `shio-demo.db` 成功，ABI 版本、统计（format_version/key_id/db_bytes）均可读，说明 JNI 未被 R8 破坏

### 编辑器 sheet 键盘遮挡

- 现象：点字段弹 sheet 后键盘出现，约 1–2 秒后 sheet 内容下移被键盘挡住
- 原因：`imePadding` 加在 sheet 内容上；窗口随 IME 改变尺寸后内容被压到可视区之外
- 修复：`Modifier.imePadding()` 改加在 `ModalBottomSheet` 的 `modifier`，内容 `Column` 改为 `verticalScroll`
- 验证：键盘弹出后等 7 秒截图，标题、输入框、「完成」按钮仍完整可见

## N13 输入框对齐 M3E

- 字段 sheet 的 `TextField` 规范化为：`surfaceContainerHighest`（聚焦）/`surfaceContainerHigh`（未聚焦）填充、16dp 顶部圆角、`primary` 聚焦指示线、`outlineVariant` 未聚焦指示线、前置字段图标、字段专属占位文案、`bodyLarge` 文本。
- `EditorField` 增加 `placeholder` 与 `icon`，五个字段分别用 标题/人像/密钥/链接/备注 图标与「给这个条目起个名字」「登录名或邮箱」「输入密码」「https://」「补充说明」占位。
- 真机验证：用户名 sheet 正常显示图标与占位，键盘弹出后输入框与「完成」按钮完整可见。
