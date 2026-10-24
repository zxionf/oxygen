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

## 环境（Windows，P0/P1 时期，历史）

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

## 构建与校验命令（Windows，历史）

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

## 环境（Arch Linux，2026-10-23 起）

- 工作区：`/home/zxionf/pros/oxygen`；内核仓库：`/home/zxionf/pros/hydrogen`。
- 系统：Arch Linux，kernel 7.2.11-zen1；`/dev/kvm` 可用。
- JDK：系统默认 `java` 21.0.13；Android Studio JBR 21.0.10（`/opt/android-studio/jbr`）。两者均可构建，未设置 `JAVA_HOME` 时也可用。
- SDK：`/home/zxionf/Android/Sdk`（`local.properties` 已指向）；platforms `android-36`、`android-36.1`；build-tools `36.1.0`。
- NDK：`29.0.14206865`（与 `ndkVersion` 一致，已用 sdkmanager 安装）；另有 `30.0.14904198-beta1`，勿选用。
- CMake：使用系统 `cmake 4.4.4`（`/usr/bin/cmake`）；`externalNativeBuild.cmake.version = "3.22.1+"`，SDK 无 cmake 包时 AGP 从 PATH 解析。SDK 内不再安装 cmake（曾装过 3.22.1，已卸载）。
- 模拟器：AVD `Medium_Phone`，API 37（Android 17）、x86_64、16 KB page size（`getconf PAGE_SIZE` = 16384）。headless 启动：
  `~/Android/Sdk/emulator/emulator -avd Medium_Phone -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`
- sele 0.2.1：`/home/zxionf/.local/bin/sele`。

## 构建与校验命令（Linux）

```bash
# 首次克隆后 gradlew 可能没有可执行位
chmod +x gradlew
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./scripts/smoke.sh          # 构建 + 安装 + 采集 ShioSmoke 结果

# APK 内 .so 的 16 KB 对齐校验
~/Android/Sdk/build-tools/36.1.0/zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk

# 检查 JNI 依赖与段对齐
readelf -d <libshio_jni.so> | grep NEEDED
readelf -lW <libshio_jni.so> | awk '/LOAD/ {print $NF}'
```

## 踩坑（Linux / P2）

- 从 Windows 迁入后 `gradlew` 丢失可执行位，需 `chmod +x`。
- AGP 默认要求精确的 CMake 版本；写成 `3.22.1+` 才会接受系统里的更高版本。若 PATH 中没有 `>= 3.22.1` 的 CMake，则仍需安装 SDK 包，或在 `local.properties` 配置 `cmake.dir`。
- `libshio_ffi.so` 没有 SONAME：用 `IMPORTED_LOCATION` 直接链接会把构建机绝对路径写进 `DT_NEEDED`，设备上加载失败。必须用 `IMPORTED_NO_SONAME TRUE` + `target_link_directories`，让链接器记录 `libshio_ffi.so`。
- AGP 的 CMake 配置缓存在 `app/.cxx`；改完链接属性后要删掉 `app/.cxx` 或执行 `:app:clean` 才会重新生成链接行。
- `sdkmanager` 的 “SDK XML version 4” 与 AGP CMake 的 CXX5304 都是无害警告。
- 模拟器提示 “different renderer configured” 时会忽略旧快照全新启动，首次开机较慢。
- 冒烟结果从 logcat 采集：`adb logcat -d -s ShioSmoke:I '*:S'`。

## P3 UI 验证命令（Linux / AVD API 37）

- 安装并启动：
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  `adb shell am start -W -n top.zxionf.shio.debug/top.zxionf.shio.MainActivity`
- UI 交互用 `adb shell input tap/text/keyevent`；坐标以 `adb shell wm size` 和截图为准（当前 1080x2400）。
- 旋转：`adb shell settings put system accelerometer_rotation 0`，再 `adb shell settings put system user_rotation 1`（恢复用 `0`）。
- `adb shell input keyevent 4` 在软键盘已收起时会直接退出 Activity；优先点界面按钮，不要用返回键收键盘。
- 截图用 `adb shell screencap -p /sdcard/x.png` + `adb pull`，避免 `exec-out` 重定向。
- 崩溃检查：`adb logcat -d -s AndroidRuntime:E '*:S'`。

## P4 设备验收命令（Linux）

- 多设备时所有 adb 命令加 `-s <serial>`，可用 `adb devices -l` 查 serial。
- 一键验收：`scripts/acceptance.sh <serial>`，包含构建、安装、SMOKE、LOCKED、杀进程持久化。
- 真机保持亮屏：`adb -s <serial> shell svc power stayon usb`；唤醒用 `input keyevent 224`。
- Compose 控件坐标用 `adb shell uiautomator dump /sdcard/ui.xml`，解析 `text=... bounds=...`，不要靠截图估算。
- 旧演示库口令未知时，先 `adb shell pm clear top.zxionf.shio.debug` 再重来。
- 真机中文输入法会把 `input text` 转成中文，内容不影响验收。

## 内核产物同步（Linux，sync-shio.sh）

- 前置：`cargo install cargo-ndk`（装到 `~/.cargo/bin`，脚本会自动回退查找）；hydrogen 的 `rust-toolchain.toml` 要求 Rust 1.95.0，并需执行 `(cd ../hydrogen && rustup target add aarch64-linux-android x86_64-linux-android)`。
- 网络受限时给 rustup 设镜像：`export RUSTUP_DIST_SERVER=https://rsproxy.cn`、`export RUSTUP_UPDATE_ROOT=https://rsproxy.cn/rustup`。
- 用法：`scripts/sync-shio.sh`；只复制 target 下已有产物用 `--skip-build`；可用 `--hydrogen` / `--ndk` 覆盖默认路径与版本。
- 脚本会同时同步 `app/src/main/cpp/include/shio.h`，并校验两个 ABI 的 ELF 架构与 16 KB LOAD 对齐。
- hydrogen HEAD 必须等于脚本固定的 `50e073b`，否则直接失败；升级内核时同步修改脚本常量与 `.design/DECISIONS.log`。

## P5 密码库 UI（Linux / Compose）

- `androidx.compose.material3:material3` 1.4.0 不再传递 `material-icons-core`；用 `Icons.Filled.*` / `Icons.AutoMirrored.Filled.*` 必须显式声明 `androidx.compose.material:material-icons-core`（BOM 管版本 1.7.8），不要引 `material-icons-extended`。
- `dynamicLightColorScheme` / `dynamicDarkColorScheme` 需要 API 31：`SDK_INT` 判断要么写在调用点，要么包进 `@RequiresApi(31)` 的私有 helper 再由调用点做版本判断；仅靠自定义 `isSupported()` 函数会让 lint `NewApi` 报错。
- `AppearancePreferences` 用 `private set` 的属性时，setter 的 JVM 签名与自定义 `setXxx` 冲突（`Platform declaration clash`），写入方法要另起名（当前为 `updateThemeMode` / `updatePalette`）。
- 外观偏好可用 adb 直接注入验证持久化：`adb push` 到 `/data/local/tmp` 后 `adb shell run-as <pkg> cp ... shared_prefs/appearance.xml`，再 force-stop 重启。
- 模拟器验证动态取色：`adb shell cmd uimode night yes|no` 切换系统深浅色；AVD `Medium_Phone`（API 37）系统动态色为低饱和蓝紫。
- 模拟器上 `adb shell input text` 只能输入 ASCII，无法验证中文关键词搜索；需要中文搜索用例时在假数据里留一个 ASCII 标记（如备注里的 `recovery-codes.txt`）。
- `adb shell input keyevent 111`（ESC）不会清空 Compose 输入框；清空要点界面上的 X，或 force-stop 后重进页面。
