#requires -Version 5.1
<#
.SYNOPSIS
    用固定提交的 hydrogen 源码重建 shio 内核产物，并同步到 app/src/main/jniLibs。

.DESCRIPTION
    1. 校验 hydrogen 当前 HEAD 是否等于脚本内固定的提交（避免产物来源漂移）。
    2. 用 cargo-ndk + 已安装 NDK 交叉编译 shio-ffi（release）。
    3. 复制 libshio_ffi.so 到 app/src/main/jniLibs/<abi>/。
    4. 用 NDK 自带 llvm-readelf 校验 ELF 架构与 16 KB page 对齐。

    脚本不修改 hydrogen 仓库（不 checkout、不 commit），只在其中执行构建。

.PARAMETER HydrogenRepo
    hydrogen 仓库路径，默认与 oxygen 同级。

.PARAMETER NdkVersion
    NDK 版本，默认为本机已安装的稳定版 r29。

.PARAMETER SkipBuild
    只复制并校验 target/<triple>/release 下已有的产物。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\sync-shio.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\sync-shio.ps1 -SkipBuild
#>
[CmdletBinding()]
param(
    [string]$HydrogenRepo = '',
    [string]$NdkVersion = '29.0.14206865',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# 固定的内核来源与 ABI 基线（改动需同步 .design/DECISIONS.log）。
$PinnedCommit = '50e073b3998c690811414aa9534393b4033cac35'
$AbiMap = [ordered]@{
    'arm64-v8a' = 'aarch64-linux-android'
    'x86_64'    = 'x86_64-linux-android'
}
$ExpectedMachine = @{
    'arm64-v8a' = 'AArch64'
    'x86_64'    = 'Advanced Micro Devices X86-64'
}
$PageAlign = '0x4000'   # 16 KB

$RepoRoot = Split-Path -Parent $PSScriptRoot
if (-not $HydrogenRepo) {
    $HydrogenRepo = Join-Path (Split-Path -Parent $RepoRoot) 'hydrogen'
}
$JniLibsRoot = Join-Path $RepoRoot 'app\src\main\jniLibs'

function Write-Step([string]$Text) {
    Write-Host "==> $Text" -ForegroundColor Cyan
}

function Resolve-SdkDir {
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { return $env:ANDROID_SDK_ROOT }
    $default = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $default) { return $default }
    throw '找不到 Android SDK：请设置 ANDROID_HOME 或 ANDROID_SDK_ROOT。'
}

function Get-ReadelfPath([string]$NdkRoot) {
    $readelf = Join-Path $NdkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe'
    if (-not (Test-Path $readelf)) {
        throw "NDK 中缺少 llvm-readelf：$readelf"
    }
    return $readelf
}

# 不依赖 Get-FileHash：部分机器的 PSModulePath 被 PS7 模块污染，cmdlet 可能不可用。
function Get-Sha256([string]$Path) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        return ([System.BitConverter]::ToString($sha.ComputeHash($stream)) -replace '-', '').ToLowerInvariant()
    } finally {
        $stream.Dispose()
        $sha.Dispose()
    }
}

# --- 0. 前置检查 -----------------------------------------------------------
if (-not (Test-Path (Join-Path $HydrogenRepo '.git'))) {
    throw "hydrogen 仓库路径无效：$HydrogenRepo"
}
$HydrogenRepo = (Resolve-Path $HydrogenRepo).Path

$sdkDir = Resolve-SdkDir
$ndkRoot = Join-Path $sdkDir "ndk\$NdkVersion"
if (-not (Test-Path $ndkRoot)) {
    throw "未安装 NDK $NdkVersion：$ndkRoot"
}
$readelf = Get-ReadelfPath $ndkRoot

# --- 1. 固定提交校验 -------------------------------------------------------
Write-Step "校验 hydrogen HEAD"
$head = (& git -C $HydrogenRepo rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'git rev-parse 失败' }
if ($head -ne $PinnedCommit) {
    throw @"
hydrogen HEAD ($head) 与脚本固定的提交 ($PinnedCommit) 不一致。
请先让 hydrogen 处于该提交（例如 git -C "$HydrogenRepo" switch --detach $PinnedCommit），
或确认来源变更后同时更新 scripts/sync-shio.ps1 与 .design/DECISIONS.log。
"@
}
$dirty = (& git -C $HydrogenRepo status --porcelain)
if ($LASTEXITCODE -ne 0) { throw 'git status 失败' }
if ($dirty) {
    Write-Warning 'hydrogen 工作区有未提交改动，产物可能不来自固定提交。'
}

# --- 2. 构建 ---------------------------------------------------------------
if ($SkipBuild) {
    Write-Step '跳过构建，使用 target 下现有产物'
} else {
    Write-Step "使用 NDK $NdkVersion 构建 shio-ffi"
    $env:ANDROID_NDK_HOME = $ndkRoot
    $env:ANDROID_NDK_ROOT = $ndkRoot

    # 显式要求 16 KB page 对齐；NDK r29 默认已是 16 KB，这里固定住避免回退。
    $pageFlag = '-C link-arg=-Wl,-z,max-page-size=16384'
    if ($env:RUSTFLAGS -and $env:RUSTFLAGS -notlike "*max-page-size=16384*") {
        $env:RUSTFLAGS = "$($env:RUSTFLAGS) $pageFlag"
    } elseif (-not $env:RUSTFLAGS) {
        $env:RUSTFLAGS = $pageFlag
    }

    & cargo ndk --version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw '缺少 cargo-ndk：请先执行 cargo install cargo-ndk。'
    }

    Push-Location $HydrogenRepo
    try {
        & cargo ndk -t arm64-v8a -t x86_64 build --release -p shio-ffi
        if ($LASTEXITCODE -ne 0) { throw 'cargo ndk 构建失败' }
    } finally {
        Pop-Location
    }
}

# --- 3. 复制 + 校验 --------------------------------------------------------
foreach ($abi in $AbiMap.Keys) {
    $triple = $AbiMap[$abi]
    $source = Join-Path $HydrogenRepo "target\$triple\release\libshio_ffi.so"
    if (-not (Test-Path $source)) {
        throw "缺少产物：$source（去掉 -SkipBuild 重新构建）"
    }

    $destDir = Join-Path $JniLibsRoot $abi
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    $dest = Join-Path $destDir 'libshio_ffi.so'
    Copy-Item -LiteralPath $source -Destination $dest -Force
    Write-Step "复制 $abi -> $dest"

    $header = (& $readelf -h $dest) | Out-String
    if ($header -notmatch [regex]::Escape($ExpectedMachine[$abi])) {
        throw "$abi 的 ELF 架构不符：期望 $($ExpectedMachine[$abi])"
    }

    $loadLines = (& $readelf -lW $dest) | Where-Object { $_ -match '^\s*LOAD\s' }
    if (-not $loadLines) { throw "$abi 没有 LOAD 段" }
    foreach ($line in $loadLines) {
        $align = ($line -split '\s+')[-1]
        if ($align -ne $PageAlign) {
            throw "$abi 的 LOAD 段 page 对齐为 $align，期望 $PageAlign（16 KB）"
        }
    }

    $hash = Get-Sha256 $dest
    $size = (Get-Item -LiteralPath $dest).Length
    "   机器=$($ExpectedMachine[$abi]) 对齐=$PageAlign 大小=$size sha256=$hash"
}

Write-Step "完成：产物来自 hydrogen $PinnedCommit（NDK $NdkVersion）"
