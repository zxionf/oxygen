# 签名与 Keystore

发布签名的材料清单与校验方式。**本文件不含任何口令**；口令只存在于仓库根的
`keystore.properties`（已 gitignore）与离线介质中。

## 当前材料

| 项 | 值 |
|---|---|
| keystore 文件 | `~/zx.jks`（PKCS12，2504 B） |
| 创建日期 | 2026-10-25 |
| alias | `zxionf`（`PrivateKeyEntry`，证书链长度 1） |
| 证书主体 / 发布者 | `O=zxionf`（自签名，X.509 v1，序列号 1） |
| 密钥 | RSA 2048，签名算法 `SHA256withRSA` |
| 有效期 | 2026-10-25 14:22 CST → 2066-10-15 14:22 CST |
| SHA-256 指纹 | `B3:CE:09:07:C9:9F:02:14:6B:B0:C5:72:34:DF:F0:3F:36:E3:0B:61:FA:7C:E8:43:7C:FC:64:B6:6F:E3:CB:44` |
| SHA-1 指纹 | `4D:51:31:00:98:8F:91:A6:E3:09:FE:8D:7D:77:71:D4:B5:D7:C3:85` |
| MD5 指纹 | `50:B6:D9:EF:B2:EB:5A:E1:EC:D2:B7:81:42:F3:19:78` |

查询命令（口令交互输入，不要写进命令历史）：

```bash
keytool -list -v -keystore ~/zx.jks
```

## 口令保管

- 口令写在仓库根的 `keystore.properties`（`storePassword` / `keyPassword`），该文件在
  `.gitignore` 中；`git ls-files | grep keystore` 为空，仓库内不含口令。
- `shio-vlt/build.gradle.kts` 只在文件存在时启用 release 签名，缺失时退回未签名产物，
  因此新克隆的仓库不会因为缺口令而构建失败。
- **口令的离线副本与 keystore 文件分开存放**，载体与位置只记录在离线清单里，不入库。

2026-10-30 核对：`git ls-files | grep keystore` 为空；`git log --all -S "<口令字面值>"`
无命中，即口令从未进过仓库历史。

## 校验

```bash
bash check.sh
```

期望输出：

- `Signer #1 certificate DN: O=zxionf`
- `Signer #1 certificate SHA-256 digest: b3ce0907c99f02146bb0c57234dff03f36e30b61fa7ce8437cfc64b66fe3cb44`
  （与上表 SHA-256 指纹一致）
- `zipalign -c -P 16` 输出 `Verification successful`（16 KB 页对齐）

2026-10-30 实测：`bash check.sh` 通过，`shio-vlt/build/outputs/apk/release/shio-vlt-release.apk`
的签名证书 SHA-256 与上表一致；证书 DN 为 `O=zxionf`。

## 恢复

见本文件的「恢复步骤」一节（`closeout.keystore.T4` 补齐）。
