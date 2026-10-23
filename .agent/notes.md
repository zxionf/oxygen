# 工具与环境

## sele CLI

- 版本 0.2.1，已在 PATH（`D:\Program Files\sele\bin\sele.exe`）。
- 命令文档见 `sele0.2.1.md`；常用：
  - `sele status` 查询活跃变更与决策数
  - `sele lint` 检查 ID 一致性、决策只追加、SPEC 禁过程词、目录完整性
  - `sele new --slug <name>` 创建变更（同时生成 `.design/changes/<id>/design.md` 与 `.agent/work/<id>/tasks.md`）
  - `sele append-decision --context <c> --decision <d> --consequences <e>` 追加决策
  - `sele close <id> --dry-run` 预览折叠，去掉 `--dry-run` 执行
- 在仓库根目录执行；不在根目录时加 `--root <path>`。

## 环境

- 工作区：`D:\zxionf\pros\oxygen`（Windows + PowerShell）。
- 内核仓库：`D:\zxionf\pros\hydrogen`（shio）。
- PowerShell 下不要使用 `ls -la`；判断命令成败优先看 `$LASTEXITCODE` 或输出末行。

## Android 工具链（2026-10-23 实测）

- JDK：`C:\Program Files\Android\Android Studio\jbr`（21.0.10）。系统默认 `JAVA_HOME` 指向 `C:\Program Files\Java\jdk-23`，构建前必须覆盖为 JBR。
- Gradle：wrapper 8.14.3（services.gradle.org，带 sha256 校验）；AGP 8.13.0；Kotlin 2.2.20。
- SDK：`C:\Users\zxionf\AppData\Local\Android\Sdk`；platforms `android-36`、`android-36.1`；build-tools `36.1.0`；cmake `3.22.1`、`4.1.2`。
- NDK：`29.0.14206865`（r29，稳定）。`27.0.12077973` 目录为空，不可用；`29.0.13113456` 为 beta1。
- 模拟器：AVD `shio_x86_64`（android-35 / google_apis / x86_64，即 API 35）。
- 本机未安装 `cargo-ndk`，`scripts/sync-shio.ps1` 目前只能用 `-SkipBuild`（复制 + 校验）。

## 构建与校验命令

```powershell
# 构建（必须用 JBR 21）
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug

# 内核产物同步 / 校验
powershell -ExecutionPolicy Bypass -File scripts\sync-shio.ps1 -SkipBuild

# APK 内 .so 的 16 KB 对齐校验
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\zipalign.exe" -c -P 16 -v 4 app\build\outputs\apk\debug\app-debug.apk
```

## 踩坑

- 本机 WSL 无 `git`；校验 hydrogen 提交需在 Windows 侧执行。
- WSL 调用 Windows exe 时 `binfmt_misc` 的 WSLInterop 偶尔掉线（`Exec format error`），重新注册：`echo ':WSLInterop:M::MZ::/init:PF' > /proc/sys/fs/binfmt_misc/register`。
- WSL 里 `cmd.exe /c "\"C:\path\app.exe\" args"` 会把引号当字面量；改用 `powershell.exe -NoProfile -Command '& "C:\...\app.exe" args'`。
- PowerShell 5.1 的 `PSModulePath` 被 PS7 模块污染，`Get-FileHash` 不可用；脚本改用 .NET `SHA256` 计算哈希。
- `res/mipmap-anydpi`（不带 `-v26`）放 `adaptive-icon` 会让 AAPT 报 `mipmap/ic_launcher not found`，保留 `mipmap-anydpi-v26`，lint 的 `ObsoleteSdkInt` 告警可忽略。
- `adb exec-out screencap -p > file` 经 PowerShell 重定向会写成 UTF-16 坏图；改用 `adb shell screencap -p /sdcard/x.png` + `adb pull`。
