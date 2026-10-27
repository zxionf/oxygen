# 20261027-import-export 任务清单

原则：实现完成后由 AI 在带窗口模拟器冒烟（用户可观察），真机验收由用户执行。

## E1 纯逻辑（可单测）

- [x] `vault/VaultTransfer.kt`：`encodeCsv` / `decodeCsv` / `encodeJson` / `decodeJson` / `detectAndDecode` / `ParseResult` / 2000 条上限
- [x] CSV 按 RFC 4180 转义（含逗号、双引号、回车换行时加引号，内部 `"` 变 `""`），`\n` 换行
- [x] JSON 用 `org.json`，2 空格缩进；导入同时接受数组与单对象
- [x] 单元测试 `VaultTransferTest` 9 例：往返一致、单对象、格式识别（含 BOM）、必填跳过、空内容、上限截断、缺列按空
- [x] 单测环境补真实 `org.json`（`testImplementation(libs.org.json)`），否则 Android stub 报 not mocked

## E2 仓库与文件读写

- [x] `VaultRepository.insertAll(entries): Int`（逐条 insert，单条失败不影响后续）
- [x] `ui/settings/VaultTransferFiles.kt`：`exportTo` / `importFrom` / `defaultFileName`
- [x] SAF 启动前 `AutoLockController.beginExternalInteraction()`，回调里 `endExternalInteraction()`

## E3 界面

- [x] 设置「数据」分组扩为 5 行：导出为 CSV / 导出为 JSON / 从文件导入 / 备份密码库 / 从备份恢复
- [x] 导出成功提示条数并警告「含明文密码」
- [x] 导入结果提示「新增 N 条，跳过 M 条」；无可识别条目时单独提示
- [x] 操作提交期间禁用入口并显示状态文案

## E4 移除 debug 样例播种

- [x] 删除 `VaultViewModel.seedSamplesOnce` 与其调用
- [x] 删除 `VaultComponent.samplesSeeded`
- [x] `SampleVaultEntries` 仅保留给 Compose 预览
- [x] 实测 debug 全新安装首解锁显示「密码库还是空的」

## E5 文档

- [x] `design.md` 记录「只新增不覆盖」的理由与格式约定
- [x] 追加决策记录（#67 导入只新增、#68 移除样例播种）

## 验证（AI 在带窗口模拟器冒烟）

- [x] 导出 CSV：`title,username,password,url,notes` 表头 + 数据行，49 字节
- [x] 导出 JSON：3 条完整字段，含 `https:\/\/` 与 `\"引号\"` 正确转义
- [x] 导入含特殊字符的 CSV：新增 2 条（Beta/Delta），跳过 1 条（Gamma 缺密码）
- [x] Beta 备注导入后为 `含逗号,和"引号"的备注`（转义往返正确）
- [x] debug 全新安装首解锁为空库
- [x] `:app:assembleDebug` 通过；单测 5 组 32 例全绿

## E6 归档

- [x] 更新 `SPEC.md`：新增「导入导出」；MVP 范围移除「CSV/JSON 导入导出」；`备份与恢复` 边界去掉「不做导入导出」；补 debug 不再播种样例
- [x] `sele close --dry-run` 确认全部 `appended` 后执行 `sele close`
