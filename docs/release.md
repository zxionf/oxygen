# 发布流程

## 版本号

- **唯一来源**：`gradle/version.properties`（入库）。`shio-vlt/build.gradle.kts` 只读取它，
  不在别处写字面量，避免版本号散落。
- **规则**：每次对外发包（分发 APK 或上架 AAB）`versionCode` 必须 **+1**，并且只改这一处；
  `versionName` 给人看，可独立调整（当前规则：功能批次变更时递增次版本号）。
- **当前**：`versionCode=6`、`versionName=0.2.0`（`0.1.5 / 3` 是上一次对外发包的值）。

同一 `versionCode` 只能发一次：已分发的号不能回收，覆盖升级依赖它递增，重发同号会导致
「无法覆盖安装」。

## 打包

```bash
./gradlew :shio-vlt:assembleRelease        # 产物 shio-vlt/build/outputs/apk/release/
./gradlew :shio-vlt:bundleRelease          # 产物 shio-vlt/build/outputs/bundle/release/（上架用）
```

签名材料与口令见 [`signing.md`](signing.md)：`keystore.properties` 存在时自动签名，
不存在时退回未签名产物（产物名带 `unsigned`），因此新克隆仓库不会构建失败。

## `.aab` 校验

`apksigner` 只认 APK，AAB 要用 `jarsigner` 与证书导出：

```bash
unzip -p shio-vlt/build/outputs/bundle/release/shio-vlt-release.aab META-INF/ZXIONF.RSA > /tmp/aab-cert.rsa
keytool -printcert -file /tmp/aab-cert.rsa | grep -E 'Owner|SHA256'   # 应为 O=zxionf 与 signing.md 的指纹
jarsigner -verify shio-vlt/build/outputs/bundle/release/shio-vlt-release.aab   # “jar 已验证”（自签名 + 无时间戳的警告属正常）
```

AAB 内的清单是二进制形式，版本号用构建产出的已打包清单核对（同一 `defaultConfig`，与 APK 同源）：

```bash
grep -o 'android:versionCode="[^"]*"' shio-vlt/build/intermediates/packaged_manifests/release/*/AndroidManifest.xml
```

## 发布前自检

```bash
bash check.sh
```

一次跑完三项，任一不符即失败或需要人工确认：

1. 签名证书 DN 为 `O=zxionf`，SHA-256 与 `signing.md` 记录一致；
2. `zipalign -c -P 16` 通过（16 KB 页对齐）；
3. 产物 `versionCode` / `versionName` 与 `gradle/version.properties` 一致。

## 检查清单

- [ ] `gradle/version.properties` 已把 `versionCode` +1（`versionName` 按需调整）
- [ ] `assembleRelease` 或 `bundleRelease` 构建成功
- [ ] `bash check.sh` 三项全过
- [ ] 真机装一次、解锁一次，能正常打开已有库
