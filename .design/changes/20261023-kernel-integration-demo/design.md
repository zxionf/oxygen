# 20261023-kernel-integration-demo

<!-- 为什么改、边界与不属于本次的范围 -->

> 历史记录：本文件写于内核集成 Demo 初期，Gradle 8.14.3 / AGP 8.13.0 / compileSdk 36 已在 `20261024-m3e-alignment` 升级；原型 Demo 不使用导航库，密码库 MVP 另行使用 Navigation Compose。当前基线以 `.design/SPEC.md` 为准。

## 目标与范围

首个变更只验证一件事：Android 应用能通过 C ABI 与 JNI 薄层完整驱动 shio 内核。

- 覆盖：建库/开库、单条写入、读取、删除、列表、批量写、统计、关闭与重开。
- 不覆盖：多端同步、改口令、数据密钥轮换、只读模式界面、发布签名、CI、任何业务功能。
- 界面只求可操作与可观察，不做视觉打磨与导航结构。

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
```

不引入 DI 框架，不拆多模块，不引入导航库。JSON 解析使用系统自带 `org.json`，避免为 Demo 增加序列化依赖。

## 内核集成

- 内核以 hydrogen 的 C ABI v1 为准，头文件为 `crates/shio-ffi/include/shio.h`，产物为 `libshio_ffi.so`。
- 本仓库只新增 JNI 薄层 `libshio_jni.so`，通过 CMake 以 `IMPORTED` 目标链接 `jniLibs/<abi>/libshio_ffi.so`，最终两个 `.so` 一起打包。
- 加载顺序：先 `System.loadLibrary("shio_ffi")`，再 `System.loadLibrary("shio_jni")`，避免依赖隐式解析。
- JNI 层不复制任何内核逻辑：只做字符串/字节数组转换、句柄表维护、错误码到异常映射、缓冲区释放与清零。

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

## 数据模型

- 命名空间固定 `demo`，集合固定 `notes`。
- 记录值固定为 `{"text":"<用户输入>"}`，单一文本字段。
- 记录 ID 使用内核生成的 UUIDv7；Kotlin 负责 16 字节与标准 UUID 字符串互转。
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

打开时选项 JSON 固定 `{"create":true}`；关闭后再次操作应显示 `SHIO_CLOSED`。重新打开前先 `destroy` 旧句柄。

## 安全与线程

- 口令只在 `String -> ByteArray` 转换后传入 native，调用结束立即清零字节数组；JVM 字符串无法清零，Demo 接受该限制。
- 口令、明文、记录正文不写日志。
- 所有 native 调用在 `Dispatchers.IO.limitedParallelism(1)` 上串行执行，避免句柄生命周期竞态；JNI 层仍用句柄表与互斥量兜底。
- Compose 只消费 `StateFlow`，不在主线程直接调用 native。

## 构建与产物

- `compileSdk 36`、`minSdk 29`、`targetSdk 36`，ABI 为 `arm64-v8a` 与 `x86_64`，NDK 使用本机已安装版本。
- 构建使用 JDK 21（Android Studio 自带 JBR）；AGP 与 Gradle 版本在脚手架阶段选定并固定到 Version Catalog / wrapper。
- 首版 `libshio_ffi.so` 直接取自 hydrogen 提交 `50e073b`；`scripts/sync-shio.ps1` 负责后续用 `cargo-ndk` 重建并复制，来源提交与 NDK 版本写入脚本常量。
- Debug 构建即可，不做签名与混淆配置。
