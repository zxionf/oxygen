# 20261023-password-vault 交接

最后更新：2026-10-25（M1–M19 完成，待 M20 归档）

## 现状一句话

密码库 MVP 已经可用：真机上从建库、增删改查、搜索、生成器、外观、解锁/锁定、自动锁定到安全收尾全部跑通；列表/详情/编辑器全部读写内核 `vault.db`（`format=2`），debug 与 release 均已验证。

## 阶段状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| M1–M3 | 导航骨架、主题系统、外观页 | 完成（M3E 变更中重做） |
| M4–M6 | 列表、详情、编辑器（假数据） | 完成 |
| M7 | 密码生成器 | 完成（`PasswordGenerator` + `PasswordGeneratorPanel` + 7 单测） |
| M8 | 设置页（含内核/ABI 版本查询） | 完成 |
| M9 | 创建/解锁 UI | 完成 |
| M10 | 导航与返回栈 | 完成 |
| M11 | 数据模型与仓库 + gate 分流 | 完成 |
| M12 | 真实创建/解锁/锁定 | 完成 |
| M13 | 真实列表与搜索 | 完成 |
| M14 | 真实详情 | 完成 |
| M15 | 真实编辑器（删除 `FakeVaultStore`） | 完成 |
| M16 | 偏好持久化与冷启动 | 完成 |
| M17 | 自动锁定 | 完成 |
| M18 | 安全收尾 | 完成 |
| M19 | 单设备验收 | 完成（见下） |
| M20 | 归档 | 待做 |

## M19 验收记录（真机 10AE4N38SA000LK / vivo V2359A / Android 16 / arm64）

验收用 debug 包（`top.zxionf.shio.debug`），每条都由 `uiautomator dump` 的控件树核对。

| 项 | 做法 | 结果 |
|---|---|---|
| 冷启动分流 | `pm clear` 后启动 | 落「创建密码库」页 |
| 建库 | 设主口令 → 创建 | 进列表；`vault.db` 1056768 B（format=2） |
| 新建 | FAB → 标题/用户名/密码/连接 → 保存 | 列表出现新条目，`insert` 生效 |
| 编辑 | 详情 → 编辑 → 改字段 → 保存 | 详情刷新为新值 |
| 删除 | 详情 → 更多 → 删除 → 确认 | 回列表，条目消失 |
| 搜索 | 输入 `bank` | 只显示命中项；删除后变「没有匹配的条目」 |
| 复制 | 详情点复制密码 | Snackbar「已复制密码」 |
| 密码显隐 | 点眼睛 → 返回 → 再进 | 显示明文，重进后恢复 `••••••••` |
| 生成器 | 密码 sheet → 生成密码 → 关大写、开排除易混淆 → 使用此密码 → 保存 | 详情显示新密码，无大写、无易混淆字符 |
| 锁定 | 顶栏锁图标 | 进解锁页；返回键直接回桌面 |
| 错误口令 | 输入 `wrongpass` | `主口令不正确（INVALID_KEY (-3)）` |
| 正确解锁 | 输入正确口令 | 进列表 |
| 自动锁定 | 默认「立即」，Home → 回来 | 停在解锁页 |
| 超时锁定 | 设「1 分钟」，后台 5s / 65s | 5s 仍在列表，65s 进解锁页 |
| 旋转 | 横屏 ↔ 竖屏 | 都在列表，不触发锁定 |
| 杀进程重开 | `am force-stop` 后启动 | 进解锁页；解锁后 8 条数据都在 |
| 外观 | 改深色 + 紫 | 立即生效，`appearance.xml` 持久化 |
| 原型 Demo | 设置 → 原型 Demo → 打开 | `shio-demo.db` 打开成功，`format_version = 1`，批量写入 3 条成功 |
| release 安全 | 装签名 release 包 | 截图全黑（`FLAG_SECURE`）；库为空白（不播种样例） |

### 验收中修掉的问题

1. **新条目被挤到搜索框上方**：`LazyColumn` 以记录 ID 作 key，新条目插入到开头时会把旧首项当锚点，导致新条目只露出半行。修法：条目数增加且列表本来在前两项内时 `scrollToItem(0)`；用户已向下翻则不打扰（`VaultListScreen`）。

## 已知限制与后续

- **debug 会播种样例**：解锁后若库为空且本进程未播过，会把 `SampleVaultEntries` 写进真实库（release 不播）。因此 debug 首次建库不会显示「空库」。这是为了在没有导入功能时能验证列表/搜索/详情。
- **无剪贴板自动清除**：目前只加 `EXTRA_IS_SENSITIVE`，自动清除属于后续阶段。
- **无改主口令/轮换/备份/导入导出**：内核已支持，JNI 未映射（见 SPEC「内核 ABI 对齐」）。
- **无多端同步 UI**：建库时已 `sync_enable`（format=2），其余同步符号未接。
- **真机自动化输入**：vivo 中文输入法会把 `adb shell input text` 的字母留在组合区，验收脚本里必须「输入后按一次回车提交」再点按钮；或用 `keyevent 123` + 多次 `67` 清空字段。

## 关键命令

```bash
# 构建与检查
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:testDebugUnitTest

# release 签名（debug keystore，口令 android）
BT=~/Android/Sdk/build-tools/36.1.0
$BT/zipalign -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/al.apk
$BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias androiddebugkey \
  --out app/build/outputs/apk/release/app-release-signed.apk /tmp/al.apk

# 内核产物同步（改 hydrogen 提交后）
scripts/sync-shio.sh            # 会同时改写 BuildConfig.SHIRO_KERNEL_COMMIT

# 真机
adb -s 10AE4N38SA000LK install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 10AE4N38SA000LK shell uiautomator dump /sdcard/x.xml && adb -s 10AE4N38SA000LK shell cat /sdcard/x.xml
```

## 数据层速查

- 数据库：`filesDir/vault.db`（`format=2`），旁路锁文件 `vault.db.lock`。
- 命名空间 `vault`，集合 `entries`；记录值 `{title, username, password, url, notes}`。
- 建库顺序：`open(create=true)` → `sync_enable` → 校验 `db_id` 与 `format_version == 2`，失败删库。
- 会话：`VaultComponent.repository`（进程级单例）；锁定 = `destroy` + 清会话 + `AutoLockController.lockCount` 通知导航清栈。
- 单测 4 组 23 例：`PasswordGeneratorTest`(7)、`VaultEntrySearchTest`(8)、`VaultUrlTest`(3)、`AutoLockPolicyTest`(5)。

## 下一步（M20）

1. `sele close 20261023-password-vault --dry-run` 检查折叠结果（应是 `appended` 而非 `replaced`）。
2. 执行 `sele close`，把 `design.md` 结论折叠进 `SPEC.md`。
3. 更新 `.design/阶段计划.md`（密码库 MVP 标记完成）与 `.agent/STATE.md`。
4. `sele lint` 全绿后结束。

## M20 归档记录（2026-10-25）

- `sele close --dry-run` 首次显示 4 节 `replaced`（会覆盖 Demo 目标与范围、demo/notes 数据模型、M10 导航规则）。按决策 #57 先把 `design.md` 三个撞名标题改成「密码库 MVP 范围 / 密码库路由表 / 密码库记录模型」，复核 9 节全部 `appended` 后执行 `sele close`。
- 折叠后去重（决策 #57）：
  - 路由表并进 `## 导航与返回栈`（做成表格），删除独立的「密码库路由表」。
  - 记录模型（含 `{"type":"login",...}` 示例与 `type` 预留说明）并进 `## 密码库数据层`，删除独立的「密码库记录模型」。
  - 删除已被现状取代的「实施原则」「页面与布局」（旧文写「卡片圆角 16dp」「不引入 material-icons-extended」，与 M3E 冲突）、「计划中的代码结构」「阶段拆分」（过程信息归 tasks.md 与阶段计划.md）、「会话与安全」（并入「安全措施」与「自动锁定」）。
  - `## 密码库 MVP 范围` 提到「项目定位」之后，作为第二大节。
  - 更新两处旧引用（「与密码库变更的关系」「目标与范围（内核集成 Demo）」）指向本文件。
- `.design/阶段计划.md`：密码库 MVP 由「进行中」改为「已归档」并列出 M1–M20 全部内容；`.agent/STATE.md` 改为 Active Change 无。
- 最终：`sele lint` 四项全绿；`assembleDebug` / `assembleRelease` / `lintDebug` / `testDebugUnitTest`（23 例）通过。
