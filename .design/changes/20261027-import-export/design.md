# 20261027-import-export

## 背景

密码库现在只能逐条手敲，也不能把数据交给别的工具或从别处迁入；同时 debug 构建会在首次解锁时往**真实库**里塞 8 条样例（`VaultViewModel.seedSamplesOnce`），既干扰使用体验，也让「空库」状态无法验证。有了导入导出，样例播种就没有存在必要。

## 产出

1. 在设置里提供「导出为 CSV」「导出为 JSON」「从文件导入」；
2. 导入采用**只新增、不覆盖**的策略，并给出「新增 N 条 / 跳过 M 条」的结果；
3. 移除 debug 样例播种（`SampleVaultEntries` 仅保留给 Compose 预览）。

## 导出

- 入口：`设置 → 数据 → 导出为 CSV` / `导出为 JSON`，走 SAF `CreateDocument`。
- 文件内容（两种格式字段一致）：`title`、`username`、`password`、`url`、`notes`。
- CSV：首行表头 `title,username,password,url,notes`；按 RFC 4180 转义（字段含 `,`、`"`、`\r`、`\n` 时整体加双引号，内部 `"` 变成 `""`）；UTF-8 无 BOM；换行 `\n`。
- JSON：`[{"title":…,"username":…,"password":…,"url":…,"notes":…}, …]`，`org.json` 生成，缩进 2 空格便于人工查看。
- 默认文件名：`shio-export-<yyyyMMdd-HHmm>.csv` / `.json`。
- 导出内容含明文密码，界面文案要提示「导出文件包含明文密码，请妥善保管」。

## 导入

- 入口：`设置 → 数据 → 从文件导入`，SAF `OpenDocument`。
- 格式识别：跳过前导空白后，首字符为 `[` 或 `{` 按 JSON 解析，否则按 CSV 解析（第一条非空行视为表头，缺列按空处理）。
- 校验：`title` 与 `password` 必填；缺任一则该条**跳过并计入跳过数**，不影响其它条目。
- 写入：逐条 `insert`（内核生成新 ID），**不查重、不覆盖**已有记录；同一文件内的重复也照新增。
- 结果：Snackbar 或对话框显示「新增 N 条，跳过 M 条」；全部失败时给出首条错误原因。
- 上限：单次最多导入 2000 条（与列表上限一致），超出部分跳过并在结果里说明。

## 为什么只新增不覆盖

覆盖需要判定「哪条算同一条」，而记录 ID 是内核生成的 UUIDv7，换设备/换库后没有稳定标识；按标题覆盖又会把一个密码更新成另一个服务的旧密码，风险更大。只新增配合「导入前先备份（C13 已有）」是更可控的路径；需要去重时的正确做法是后续单独做「合并/去重」变更。

## 设置入口

- 「数据」分组从 2 行扩展为 4 行：`导出为 CSV`、`导出为 JSON`、`从文件导入`、`备份密码库`、`从备份恢复`（共 5 行，按操作风险排序：导入/导出在前，备份/恢复在后）。
- 导入导出的 SAF 启动前调用 `AutoLockController.beginExternalInteraction()`，回调里 `endExternalInteraction()`（与 C13 一致）。
- 操作提交期间禁用入口并显示「正在读取…/正在导出…」。

## 实现要点

- 新增 `vault/VaultTransfer.kt`（纯 Kotlin，无 Android 依赖，便于单测）：
  - `encodeCsv(entries): String` / `decodeCsv(text): ParseResult`
  - `encodeJson(entries): String` / `decodeJson(text): ParseResult`
  - `detectAndDecode(text): ParseResult`（按首字符分流）
  - `ParseResult(entries: List<VaultEntry>, skipped: Int)`
- 新增 `ui/settings/VaultTransferFiles.kt`：SAF 读写、临时文件、上限检查（与 `VaultBackupFiles` 同构）。
- `VaultRepository` 增加 `insertAll(entries): Int`（逐条 insert，返回成功数）。
- SV 移除：`VaultViewModel.seedSamplesOnce`、`VaultComponent.samplesSeeded`、`BuildConfig` 导入（若不再使用）。

## 边界

- 不导出 `id`、时间戳、`type`（导入时一律新建、`type` 固定 `login`）。
- 不解析第三方密码管理器专有格式（1Password/Bitwarden 的 CSV 需要用户先转换列名）。
- 不做导入预览/逐条勾选；不做去重与合并。
- 导出文件不带任何加密，属于用户自行保管的明文副本。

## 检查项

- 导出 JSON / CSV 后能看到表头与全部条目，字段顺序正确。
- 清空库（新建库）→ 导入刚导出的 JSON → 条目数量与内容一致。
- 导入含缺 `password` 的行 → 该行跳过，其余正常导入，结果提示跳过数。
- CSV 中含逗号、双引号、换行的备注 → 导出后再导入，内容完全一致（转义正确）。
- debug 全新安装后首次解锁：库为空，不再自动出现 8 条样例。
