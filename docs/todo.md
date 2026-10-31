# 待办事项

最后更新：2026-10-31。

> **本文件是待办的唯一真相。** 原 `.sele` 里的 plan/line/task/step 结构在 2026-10-31 因工具链升级而暂停，
> 现有状态冻结如下（结构、依赖、步骤全部照搬）。工具链升级完成后，可按本文件重建 sele 结构，
> 或直接按本文逐条推进。

## 零、已冻结的状态

| 计划 | 状态 | 说明 |
|---|---|---|
| `closeout` | **done 并归档** | A 组真机验证（A1–A7）、B2 导入导出标签与收藏；发布相关（商店素材/打包/签名/keystore）已按决定取消 |
| `sync` | active，**暂停** | 设计线完成一半（传输层 + TLS 已定），实现线未开工 |
| `features` | active，**未开工** | B3 / B4 / B5 / D1–D3 |
| `relay`（未建） | — | 中继节点，规格已写到 `../shio/docs/relay.md` |

A 组验证的逐条证据见 [`verify-a-group.md`](verify-a-group.md)。

## 一、同步（`sync` 计划）

### 1.1 设计线 `sync-design`（T1 done、T2 差人工评审、T3–T5 待做）

已定结论（详见 `.design/sync-design.md` 与 `../shio/docs/relay.md`）：

- 传输层：**自建中继节点**（不做局域网直连——校园网客户端隔离；不做第三方云中继；文件式同步暂不做）
- 中继实现：**Rust，放在 shio 内核仓库 `../shio`**；客户端只做调用方
- 中继定位：**与业务无关的帧信箱**，只认 `db_id`，密码库/笔记/待办共用
- TLS：公网必须 TLS（前置 Caddy/nginx 或内置 rustls）；客户端用系统信任库、不做 pinning、不提供跳过校验；禁明文 HTTP
- 令牌：设备级（首个令牌用 `relay token issue` 签发），新设备通过老设备签发的一次性入网码加入
- 游标：中继分配每库单调 `seq`，与内核 `after` 分离
- 保留：全部设备 ack / 30 天 / 100 MB 淘汰最旧；淘汰后客户端走快照全量重同步
- 范围：第一版 changes/apply/snapshot/pairing；`offers`/`rotation*` 第二版；`migrate*`/`compact`/`prune_offers`/`retire` 不接
- 后台：不做自动拉取，同步页手动触发

- [ ] **T2 人工评审收尾**（S3 进行中）：确认 TLS 责任边界（`.design/sync-design.md` §5）
- [ ] **T3 配对密钥的展示/存储/撤销**：S1 配对流程与密钥生命周期 → S2 展示形式（文本/二维码/短码）→ S3 人工评审
- [ ] **T4 冲突呈现与后台拉取策略**：S1 静默 LWW vs 冲突提示 → S2 后台拉取触发与频率 → S3 人工评审
- [ ] **T5 定稿讨论稿并落 decision**：S1 合并为设计文档 → S2 每项取舍记一条 decision

### 1.2 中继节点线（新线，未建；规格已完成）

规格：`../shio/docs/relay.md`（167 行，含 API/存储/GC/限流/部署/测试计划）。

- [ ] 在 `../shio` 新建 crate（建议 `crates/shio-relay`）：sqlite(WAL) + HTTP v1 + 令牌命令行
- [ ] 帧头校验（`magic=SHY2`/`format=2`/`db_id`/`sender`/`kind`）与 4 MiB 上限
- [ ] `/v1/frames`（推/拉）、`/v1/ack`、`/v1/info`、`/v1/devices*`（入网码/换令牌/撤销）、`/healthz`
- [ ] 幂等键去重 + per-db 串行 + `seq` 单调
- [ ] GC：全设备 ack / TTL / 配额，推进 `truncated_from`
- [ ] `relay serve|token issue|token revoke|stats|gc` 命令行
- [ ] 部署：TLS 前置反代 + systemd；日志卫生（不记令牌/帧内容，IP 留 7 天）
- [ ] 测试：帧头校验单测、两客户端推拉 ack 集成、淘汰后 `truncated_from` 行为

### 1.3 客户端内核映射线 `sync-kernel`（全未开工）

- [ ] **T1 changes/apply/offers 三族**：S1 JNI + `NativeShio` → S2 `ShioDatabase` 封装与错误码映射 → S3 单测
- [ ] **T2 snapshot 族**：S1 创建/读取符号 → S2 Kotlin 封装 → S3 冒烟生成本地快照
- [ ] **T3 rotation 与 pairing 族（含 `open_with_pairing`）**：S1 rotation → S2 pairing → S3 失败路径与错误码
- [ ] **T4 单机自同步冒烟与 SPEC 更新**：S1 构造两类并发变更 → S2 合并往返 → S3 更新 SPEC 的 ABI 对齐清单

### 1.4 同步 UI 线 `sync-ui`（全未开工）

- [ ] **T1 同步设置页**：S1 页面骨架（`StackedList`/`FieldItem`）→ S2 状态与错误文案 → S3 快照与轮换入口占位
- [ ] **T2 配对文本与二维码**：S1 生成与解析配对文本 → S2 二维码渲染 → S3 失败与撤销路径
- [ ] **T3 冲突提示**：S1 冲突模型到 UI 映射 → S2 逐条处理（保留本地/采用远端）→ S3 单测
- [ ] **T4 端到端验收**：S1 两机配对 → S2 双向变更合并 → S3 冲突场景验证

## 二、功能与工程质量（`features` 计划）

- [ ] **B3 生物识别（`biometric`）**
  - T1 方案设计（Keystore 包裹主口令 + BiometricPrompt）
  - T2 凭据封装与解锁集成
  - T3 失效与锁定策略（登记变更/失败次数/设置开关）
  - T4 真机验收（解锁、取消、锁定、登记变更）
- [ ] **B4 密钥轮换（`key-rotation`）**
  - T1 映射 `shio_rotate_data_key`（JNI + 封装 + 单测）
  - T2 设置入口与失败处理
  - T3 验收：轮换后条目可读、旧备份不可解
- [ ] **B5 跨库移动与迁移（`vault-ops`）**
  - T1 仓储层跨库移动 + 单测
  - T2 UI 单条/批量选择目标库
  - T3 库迁移到另一台设备的步骤文档
- [ ] **D1–D3 工程质量（`quality`）**
  - T1 CI：`assembleDebug` + `lintDebug` + `testDebugUnitTest`
  - T2 `VaultRepository`/`VaultViewModel` 单测 + 关键 Compose 页仪器测试
  - T3 `VaultLibrary` Context 路径单测（register/remove/legacy）

## 三、已完成（留档）

- 密码库 MVP M1–M20、C11 剪贴板清除、C12 改主口令、C13 备份恢复、C21 标签收藏、C22 导入导出、C23 多密码库
- **B2 导入导出支持标签与收藏**：CSV `tags`/`favorite` 列、JSON 数组与布尔、导入按标签名匹配或创建（真机往返回归通过）
- **A1–A7 真机验证**（vivo V2359A / Android 16，逐条证据见 `verify-a-group.md`）
- 版本号单一来源 `gradle/version.properties` + `check.sh` 校验（签名 / 16 KB 对齐 / 版本号）
- 发布相关（C1–C5）按决定取消：不上架，相关文档已删除

## 四、工具链升级后的第一步

1. 确认 `.sele` 是否可迁移；不能迁移就按本文件重建 plan/line/task（`sync` + `features` 两张表已列全）。
2. 恢复 `sync.sync-design.T2` 的 S3（人工评审）后继续 T3–T5。
3. 新开中继线：`../shio/docs/relay.md` 已可当规格直接开工。
