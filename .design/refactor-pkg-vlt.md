# 包名重构：`top.zxionf.shio` → `top.zxionf.shio.vlt`

> `refactor.pkg-vlt` 的 T1 产物（2026-10-31）。目标：密码库应用的包名与 `applicationId`
> 与 `shio-todo`（`top.zxionf.shio.todo`）形成对称结构。

## 1. 现状清单

| 位置 | 现状 | 目标 |
|---|---|---|
| `shio-vlt/build.gradle.kts` | `namespace = "top.zxionf.shio"`、`applicationId = "top.zxionf.shio"` | `top.zxionf.shio.vlt`（debug 仍加 `.debug`） |
| `shio-vlt/src/{main,test,debug}/java/top/zxionf/shio/**` | 58 个 Kotlin 文件声明 `top.zxionf.shio.*` | 目录移到 `.../shio/vlt/**`，包名 `top.zxionf.shio.vlt.*` |
| `shio-vlt/src/main/AndroidManifest.xml` | activity 用相对名 `.MainActivity`，无 `package` 属性 | 无需硬编码包名（namespace 决定），但要复核 |
| `shio-vlt/proguard-rules.pro` | `-keep class top.zxionf.shio.kernel.NativeShio` | **保持不变**（属于 shared） |
| `scripts/acceptance.sh` / `scripts/smoke.sh` | `top.zxionf.shio.debug`、`top.zxionf.shio.debug.SmokeActivity` | `top.zxionf.shio.vlt.debug` |
| `.design/SPEC.md`、`docs/vault.md`、`docs/todo.md` | 提到包名 `top.zxionf.shio` | 同步为 `top.zxionf.shio.vlt` |
| `.agent/STATE.md` | 提到包名 | **删除该文件**（人类决定：不再维护） |
| `shared/` | `top.zxionf.shio.shared` / `top.zxionf.shio.kernel` | **不动** |

包名在 `shio-vlt` 的分布（58 个文件）：

```
22  top.zxionf.shio.vault
 7  top.zxionf.shio.ui.vault
 6  top.zxionf.shio.ui.theme
 6  top.zxionf.shio.ui.settings
 6  top.zxionf.shio.ui.components
 3  top.zxionf.shio.app
 2  top.zxionf.shio.demo
 2  top.zxionf.shio.debug
 1  top.zxionf.shio.ui.unlock
 1  top.zxionf.shio           （MainActivity）
```

## 2. 边界：`shared` 与 JNI 不动

- `NativeShio` 在 `shared` 的 `top.zxionf.shio.kernel` 包里；JNI 入口名是
  `Java_top_zxionf_shio_kernel_NativeShio_*`（`shared/src/main/cpp/shio_jni.cpp`，共 30+ 处）。
- 因为 shared 的一个字节都不改，`shio_jni.cpp`、`proguard-rules.pro` 的 keep 规则、`libshio_jni.so` 全部保持原样。
- `shio-vlt` 对 shared 的 import（`top.zxionf.shio.kernel.*`、`top.zxionf.shio.shared.*`）**也不能改**——
  它们指向另一个模块。这是本次迁移最容易误伤的地方，改写时要按「只替换 `top.zxionf.shio.` 后紧跟
  应用内子包（app/ui/vault/demo/debug）或包结尾」的规则处理，不能无脑全局替换。

## 3. 映射表

| 旧 | 新 |
|---|---|
| `top.zxionf.shio`（MainActivity 所在包） | `top.zxionf.shio.vlt` |
| `top.zxionf.shio.app` | `top.zxionf.shio.vlt.app` |
| `top.zxionf.shio.ui.*` | `top.zxionf.shio.vlt.ui.*` |
| `top.zxionf.shio.vault` | `top.zxionf.shio.vlt.vault` |
| `top.zxionf.shio.demo` | `top.zxionf.shio.vlt.demo` |
| `top.zxionf.shio.debug` | `top.zxionf.shio.vlt.debug` |
| `top.zxionf.shio.kernel.*` | 不变（shared） |
| `top.zxionf.shio.shared.*` | 不变（shared） |

目录映射：`shio-vlt/src/{main,test,debug}/java/top/zxionf/shio/` → `.../java/top/zxionf/shio/vlt/`
（`MainActivity.kt` 从 `shio/` 移到 `shio/vlt/`，其余子目录整体下移一层）。

## 4. `applicationId` 变更的影响

- Android 视角是**另一个应用**：`top.zxionf.shio.vlt` 不会继承 `top.zxionf.shio` 的数据，
  也不能覆盖安装；旧包需要手动卸载。
- 备份互不相通：新包只能通过「导出/备份文件 → 导入/恢复」搬运数据（由人类自行完成，不在本线范围）。
- 签名不变（同一 keystore），所以新旧包可以共存安装。
- `versionCode` 沿用 `gradle/version.properties`，与包名无关。

## 5. 验收口径

1. `:shio-vlt:{assembleDebug,lintDebug,testDebugUnitTest,assembleRelease}` 全部通过；
2. `aapt2 dump badging` 显示 `package: name='top.zxionf.shio.vlt'`；
3. 真机装新包，建库 → 建条目 → 导出 → 导入跑通；
4. 全仓 `grep -r "top\.zxionf\.shio\b"` 只剩 shared/内核/历史归档中的正当引用。
