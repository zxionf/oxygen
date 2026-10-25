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

## Android 工具链（2026-10-23 实测，历史）

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

## 当前工具链（2026-10-24 M3E 后）

- Gradle 9.8.2、AGP 9.4.1、Kotlin 2.4.21（AGP 内置 Kotlin 支持）、JDK 21。
- `compileSdk` / `targetSdk` 37，`minSdk` 29；Compose BOM 2026.09.00，material3 1.5.0-beta02，navigation 2.10.2，activity 1.13.0，lifecycle 2.11.0，core-ktx 1.19.1。
- NDK `29.0.14206865`；CMake `3.22.1+`（使用系统 4.4.4）。
- 图标为 `material-icons-extended`（`AppIcons` / `AppIcon`），不再使用 Material Symbols Rounded 字体；release 开启 R8，JNI 入口由 `proguard-rules.pro` 保留。
- 密码库已接 `vault.db`：列表/详情/编辑器全部走 `VaultRepository`；debug 解锁后若库为空会用 `SampleVaultEntries` 播种。
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
- hydrogen HEAD 必须等于脚本固定的 `5f3e8e3`，否则直接失败；升级内核时同步修改脚本常量与 `.design/DECISIONS.log`。

## P5 密码库 UI（Linux / Compose）

- `androidx.compose.material3:material3` 1.4.0 不再传递 `material-icons-core`；用 `Icons.Filled.*` / `Icons.AutoMirrored.Filled.*` 必须显式声明 `androidx.compose.material:material-icons-core`（BOM 管版本 1.7.8），不要引 `material-icons-extended`。
- `dynamicLightColorScheme` / `dynamicDarkColorScheme` 需要 API 31：`SDK_INT` 判断要么写在调用点，要么包进 `@RequiresApi(31)` 的私有 helper 再由调用点做版本判断；仅靠自定义 `isSupported()` 函数会让 lint `NewApi` 报错。
- `AppearancePreferences` 用 `private set` 的属性时，setter 的 JVM 签名与自定义 `setXxx` 冲突（`Platform declaration clash`），写入方法要另起名（当前为 `updateThemeMode` / `updatePalette`）。
- 外观偏好可用 adb 直接注入验证持久化：`adb push` 到 `/data/local/tmp` 后 `adb shell run-as <pkg> cp ... shared_prefs/appearance.xml`，再 force-stop 重启。
- 模拟器验证动态取色：`adb shell cmd uimode night yes|no` 切换系统深浅色；AVD `Medium_Phone`（API 37）系统动态色为低饱和蓝紫。
- 模拟器上 `adb shell input text` 只能输入 ASCII，无法验证中文关键词搜索；需要中文搜索用例时在假数据里留一个 ASCII 标记（如备注里的 `recovery-codes.txt`）。
- `adb shell input keyevent 111`（ESC）不会清空 Compose 输入框；清空要点界面上的 X，或 force-stop 后重进页面。

## P6 密码库 UI 二期（M5/M6，Linux / Compose 1.4.0）

- 多设备时所有 adb 命令必须加 `-s <serial>`（`emulator-5554` 与真机同时在线时，裸 `adb install` 会报 `more than one device/emulator`）。
- `input keyevent 4` 第一次只收键盘，第二次才进 `BackHandler`；截图/断言前先确认键盘已收，否则会误判「返回没弹确认框」。
- 在 `@Composable` 里读 `StateFlow.value`（如预览里 `store.entries.value`）会被 lint 判 `StateFlowValueCalledInComposition` 并中断构建；预览改用静态样本，界面用 `collectAsStateWithLifecycle`。
- `material3 1.4.0` 的 `MaterialExpressiveTheme`、`MotionScheme.standard()/expressive()`、`MaterialTheme.motionScheme`、`Typography.*Emphasized` 都是 internal，应用层取不到；只能按同样参数本地复刻动效，形状/字重用显式值对齐。
- `material-icons-core` 只有 40 余个图标（无 Copy/Visibility/Link/Key/Title/Description）；用 `ImageVector.Builder` + `addPathNodes(路径)` 本地构建，避免 `material-icons-extended`。
- `IconButton` / `Surface(onClick)` 都接受 `interactionSource`，配合 `collectIsPressedAsState()` + `animateFloatAsState()` 可做「ripple + 按下缩放」。
- 签名 release APK（无正式 keystore 时）：
  ```bash
  ./gradlew :app:assembleRelease
  BT=~/Android/Sdk/build-tools/36.1.0
  $BT/zipalign -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/aligned.apk
  $BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
      --ks-key-alias androiddebugkey --out app/build/outputs/apk/release/app-release-signed.apk /tmp/aligned.apk
  $BT/apksigner verify app/build/outputs/apk/release/app-release-signed.apk
  $BT/zipalign -c -P 16 4 app/build/outputs/apk/release/app-release-signed.apk
  ```
- `BuildConfig` 默认关闭，AGP 8 需在 `buildFeatures { buildConfig = true }` 才能区分 debug/release 行为。

## P7 M3E 对齐（工具链大升级）

- Gradle 9 + JDK 21 在部分环境下 `configureCMake*` 会报 `Failed to exec spawn helper`；在 `gradle.properties` 的 `org.gradle.jvmargs` 里加 `-Djdk.lang.Process.launchMechanism=fork` 即可。
- AGP 9 起 Kotlin 支持内置：必须移除 `org.jetbrains.kotlin.android` 插件，否则报「no longer required」；`kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }` 也随之删除（`JvmTarget` 不再在 buildscript 类路径，jvmTarget 跟 `compileOptions` 对齐，产物仍是 Java 17）。
- material3 1.5.0-beta02 要求 `compileSdk 37` 且 `minAndroidGradlePluginVersion=9.1.0`；`platforms;android-37.0` + `build-tools;37.0.0` 需用 sdkmanager 安装。
- material3 1.5.0 里 `MaterialExpressiveTheme`、`MaterialTheme.motionScheme`、`ShapeDefaults.LargeIncreased`、`Shapes(...)` 构造器、`Typography.*Emphasized` 全部公开；`rememberModalBottomSheetState` 已废弃，改用 `rememberBottomSheetState(initialValue = SheetValue.Hidden)`（`ModalBottomSheet` 会在组合后自动 `show()`）。
- 图标不再用 `material-icons-*`：用 Google Fonts CSS 的 `text=` 子集接口（`User-Agent: Mozilla/4.0` 会返回 TTF）生成只含所需码位的字体：
  ```bash
  CP="e5cd e668 ..."; TEXT=""; for c in $CP; do TEXT="$TEXT$(printf "\\u$c")"; done
  curl -sG "https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0" \
    --data-urlencode "text=$TEXT" -H "User-Agent: Mozilla/4.0" -o m3e.css
  URL=$(grep -oE 'https://fonts\.gstatic\.com/[^)]+' m3e.css); curl -sL -o app/src/main/res/font/material_symbols_rounded.ttf "$URL"
  fc-scan --format "%{charset}\n" app/src/main/res/font/material_symbols_rounded.ttf   # 校验覆盖
  ```
  32 个字形约 7.4 KB；FILL 轴用 `FontVariation.Setting("FILL", 0/1)` 切换描边/实心。
- 字体图标用 `Text` 渲染（`Icon` 只吃 `ImageVector`/`Painter`），需手动补/清语义。

## P8 M3E 细节修正

- 字体图标（Material Symbols）用 `Text` 渲染时默认行高会把图标压低几像素；`SymbolIcon` 里必须设 `PlatformTextStyle(includeFontPadding = false)` + `LineHeightStyle(Alignment.Center, Trim.Both)`，否则 48dp 图标按钮与 40dp 图标位里的图标看起来「偏下」。
- `ToggleButton` 即使只给 `icon`（`content = {}`）也带默认水平内边距，塞进输入框 trailing 会显得很宽；密码显隐这类 48dp 开关应用 `IconToggleButton`（有 checked 语义、宽度正常）。
- M3E 的分组列表要「堆叠」：同组条目用 `StackedList`（3dp 间距、外 28dp / 内 8dp），不要用一张大 Surface + 分隔线。

## P9 图标与构建

- 字体图标（Material Symbols）在圆形图标按钮里始终有几像素下移，改用 `material-icons-extended`（BOM 1.7.8）的矢量图标后消失；业务名统一走 `ui/components/Icons.kt` 的 `AppIcons` 枚举 + `AppIcon`。
- 代价：未开 R8 时 debug APK 约 69 MB、release 约 52 MB（原 34 / 27 MB）。要瘦身需在 release 打开 `isMinifyEnabled` + `isShrinkResources`，或换回核心图标集。
- 用户要求：不启动模拟器，所有验证直接在真机 `10AE4N38SA000LK`（vivo V2359A / Android 16）上做。

## P10 内核升级与版本溯源

- 升级内核要同时动四处：`scripts/sync-shio.sh` / `sync-shio.ps1` 的 `PINNED_COMMIT`、`include/shio.h`（脚本会同步，sha256 写进 `cpp/README.md`）、`jniLibs/*/libshio_ffi.so`（脚本复制并校验 16 KB 对齐）、`app/build.gradle.kts` 的 `SHIO_KERNEL_COMMIT`。
- 2026-10-25 踩过：只改了脚本和文档，忘了 `SHIO_KERNEL_COMMIT`，设置页「内核提交」还显示旧哈希。现在两个同步脚本在结尾用 awk 把 `SHIO_KERNEL_COMMIT` 下一行的 40 位哈希改写成 `PINNED_COMMIT`，跟着脚本走就不会漏。
- awk 正则别写 `"[0-9a-f]{40}"`：Kotlin 里是 `"\"<hash>\""`，哈希后面紧跟的是反斜杠，带引号的模式匹配不到；直接 `gsub(/[0-9a-f]{40}/, commit)`，并用「上一行是 SHIO_KERNEL_COMMIT」限定作用域。
- 内核版本查询用 `shio_version()`（返回编译期包版本，如 `0.2.0`），ABI 版本仍用 `shio_abi_version()`；两者都不需要打开数据库，JNI 层直接 `NewStringUTF` 复制静态指针即可。

## P11 跨仓库 ABI 审计（hydrogen 5f3e8e3）

- 审计方式：`diff app/src/main/cpp/include/shio.h ../hydrogen/crates/shio-ffi/include/shio.h`（必须一致）、`llvm-readelf --dyn-syms` 对照 `Java_..._NativeShio_*` 导出、头文件 `SHIO_*` 常量对照 `ShioException`。
- 2026-10-25 查到：`SHIO_CANCELLED (-23)` 漏了（内核在存储维护 S1 新增），已补。
- 内核 Android 侧的新事实：redb 字节范围锁在 Android 不可用，改由 facade 在库旁维护 `<db>.lock` + `flock`（写独占/读共享），冲突是 `SHIO_LOCKED`；因此同进程只能有一个写句柄，备份/清理不能在持有期间删目录。
- 内核决策要求「Android 首次建库必须 `sync_enable` 走 format=2」（hydrogen DECISIONS [40]），与本仓库 MVP「不做同步」的范围存在冲突，建库流程（M11/M12）开工前必须先定。
- 内核 `.so` 按 API 24 基线链接，应用 minSdk 29，两者不必一致；`shio_open` 的 options 只认 `create`/`read_only`/`max_value_size`/`cache_size`。

## P12 真机验证搜索的技巧

- vivo 默认输入法是中文拼音：`adb shell input text "net"` 只会进输入法的组合区，不会提交给应用，界面上看不见。要么先 `input keyevent 66`（回车/提交组合），要么换 ASCII 输入法；否则会误判成「搜索框不吃输入」。
- 验证搜索覆盖度用样例数据里的独有片段最省事：标题（`net`→Netflix）、用户名（`6222`→银行 App）、备注（`recovery`→GitHub）、无结果（`zzzz`）。

## P13 真机验证自动锁定

- 用 `adb shell input keyevent 3` 回桌面模拟切后台，`am start -n <pkg>/<activity>` 回前台；日志里会出现 “Activity not started, its current task has been brought to the front”，属正常。
- 验证「立即」锁定：先回桌面 2–3 秒再回前台，应停在解锁页；验证「1 分钟」：后台 5 秒回来应仍在列表，后台 65 秒回来应在解锁页。
- 旋转用 `settings put system user_rotation 1/0`（先 `accelerometer_rotation 0`），手机横过来后 `uiautomator dump` 仍应看到列表内容，证明 `isChangingConfigurations` 分支生效。
- 解锁页按返回键应直接回到桌面：锁定时 `popUpTo(graph.id){inclusive=true}` 已把列表等全部清掉。

## P14 release 验收与 FLAG_SECURE

- release 包要自己 zipalign + apksigner 签名（debug keystore，口令 `android`），否则 `install` 会被拒。
- 验证 FLAG_SECURE 生效最直接的办法：`adb exec-out screencap -p` 得到的 PNG 里除了状态栏全黑；`uiautomator dump` 仍能拿到控件树，所以功能验证不受影响。
- release 首次安装后库是空的（没有 debug 样例播种），可以顺便确认「release 不带样例数据」。
- `convert ... -format "%[fx:mean]" info:` 可以用平均亮度快速判断截图是黑屏还是真实界面。

## P15 sele close 的标题匹配陷阱

- `sele close` 判断「替换还是追加」是按标题模糊匹配的：设计文档里的 `## 修改主口令目标` 和 `## 修改主口令验证` 都被判成要替换 SPEC 里已有的 `## 修改主口令`，结果手写的那节被**验证记录**覆盖。
- 规避：设计文档的小节名不要以 SPEC 已有标题为前缀（如 `修改主口令目标/修改主口令验证` 都是 `修改主口令` 的前缀）。改成 `变更目标`、`验证要点` 这类完全不同的词，或直接在 close 前把对应 SPEC 小节临时改名。
- 另一个坑：`awk '/^## X$/{skip=1}...'` 清理小节时容易连自身内容一起删掉；改 SPEC 后务必 `grep -A3 '^## 小节名'` 复核正文还在。
