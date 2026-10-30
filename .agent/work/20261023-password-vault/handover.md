# 20261023-password-vault 交接

最后更新：2026-10-27（密码库 MVP M1–M20 与后续 C11–C23 全部归档）

## 现状一句话

三个 Gradle 模块：`shio-vlt`（密码库应用，`top.zxionf.shio`）、`shared`（JNI 包装与内核封装，通用 UI 占位）、`shio-todo`（待办应用占位）。密码库已可用：多库、条目 CRUD、搜索、标签/收藏、密码生成器、导入导出、备份恢复、改主口令、自动锁定、剪贴板自动清除、release 禁止截屏；全部读写内核 `vault.db`（`format=2`）。

## 阶段状态

| 批次 | 内容 | 状态 |
|---|---|---|
| M1–M10 | 导航、主题、外观、列表、详情、编辑器、生成器、设置、创建/解锁、返回栈 | 完成 |
| M11–M15 | 数据层、真实会话、列表、详情、编辑器（删 `FakeVaultStore`） | 完成 |
| M16–M18 | 偏好持久化、自动锁定、安全收尾 | 完成 |
| M19–M20 | 真机单设备验收、归档 | 完成 |
| C11 | 剪贴板自动清除（30 秒 + 锁定时 + 只清自己内容 + 输入法边界提示） | 归档并验收 |
| C12 | 改主口令（`shio_change_password`） | 归档并验收 |
| C13 | 备份与恢复（SAF + `shio_backup`/`shio_restore` + 外部交互豁免） | 归档并验收 |
| C21 | 标签与收藏（独立 `vault/tags` 集合 + 条目 `tagIds` 引用 + 筛选 chips） | 归档（真机待验） |
| C22 | 导入导出（CSV/JSON，只新增不覆盖；移除 debug 样例播种） | 归档（真机待验） |
| C23 | 多密码库（`SharedPreferences` 索引 + `filesDir/vaults/<uuid>.db`） | 归档（真机待验） |
| 第三波 | 多端同步 | 未开始，需先定传输层与信任模型 |

## 已知限制与后续

- **同步**：建库时已 `sync_enable`（`format=2`），但除 `sync_enable`/`sync_info` 外的 18 个同步符号未接入，无同步 UI、配对与网络。
- **未映射的内核符号**：`shio_rotate_data_key`（密钥轮换）、`shio_flush`（写入均以单条/批量提交，未需要显式屏障）。
- **导入导出不含标签与收藏**：CSV/JSON 仍只有五个业务字段；标签随条目迁移留到后续变更。
- **同一时刻只有一个活动会话**：内核句柄是进程级单例，切换库需要重新输入目标库主口令。
- **剪贴板**：系统剪贴板会在 30 秒/锁定时清除；输入法自带的「剪贴板历史」属其私有存储，应用无法删除（已用首次提示告知并给出跳转）。
- **真机待验**（C21/C23）：多条目下标签筛选与收藏叠加、重命名/删除标签联动、重命名/删除库、跨库隔离、旧 `vault.db` 自动注册。
- **真机自动化输入**：vivo 中文输入法会把 `adb shell input text` 的字母留在组合区，必须「输入后回车提交」再点按钮；或 `keyevent 123` + 多次 `67` 清空字段。

## 关键命令

```bash
# 构建与检查（三个模块：shio-vlt 应用 / shared 内核包装 / shio-todo 占位）
./gradlew :shio-vlt:assembleDebug :shio-vlt:assembleRelease :shio-vlt:lintDebug :shio-vlt:testDebugUnitTest
./gradlew :shared:assembleDebug :shio-todo:assembleDebug

# release 签名（keystore.properties 配好后 assembleRelease 自动签名）
./gradlew :shio-vlt:assembleRelease   # → shio-vlt/build/outputs/apk/release/shio-vlt-release.apk

# 校验签名与对齐
bash check.sh                          # 内部用 build-tools 36.1.0 跑 apksigner + zipalign

# 内核产物同步（升级 hydrogen 时，产物落在 shared/）
scripts/sync-shio.sh            # 同时改写 shared/build.gradle.kts 的 SHIO_KERNEL_COMMIT

# 真机 / 模拟器
adb -s 10AE4N38SA000LK install -r shio-vlt/build/outputs/apk/debug/shio-vlt-debug.apk
adb -s emulator-5554 shell uiautomator dump /sdcard/x.xml && adb -s emulator-5554 shell cat /sdcard/x.xml
```

## 数据层速查

- 多库索引：`SharedPreferences` 文件 `vaults.xml`，键 `entries`（JSON 数组：`id`/`name`/`fileName`/`createdAt`）、`lastVaultId`。
- 库文件：`filesDir/vaults/<uuid>.db`；升级前的 `filesDir/vault.db` 作为 `default` 库原地保留。
- 集合：命名空间 `vault`，`entries`（条目）+ `tags`（标签）；条目通过 `tagIds` 引用标签。
- 条目字段：`title`/`username`/`password`/`url`/`notes`/`tagIds`/`favorite`。
- 建库顺序：`open(create=true)` → `sync_enable` → 校验 `db_id` 与 `format_version == 2`，失败删库。
- 会话：`VaultComponent.repository`（进程级单例）；锁定 = `destroy` + 清会话 + `AutoLockController.lockCount` 通知导航清栈。
- 单测 7 组 46 例：`PasswordGeneratorTest`(7)、`VaultEntrySearchTest`(8)、`VaultUrlTest`(3)、`AutoLockPolicyTest`(5)、`VaultTransferTest`(9)、`VaultTagTest`(8)、`VaultLibraryTest`(6)。

## 下一步候选

1. 第三波同步（先讨论传输层：局域网直连 vs 自建服务；再定信任模型与冲突呈现）。
2. 导入导出支持标签（依赖 C21 的模型已稳定）。
3. 发布收尾：商店素材、隐私说明（应用无网络权限，说明成本低）、版本策略。
4. 真机补验 C21/C23 的遗留项。
