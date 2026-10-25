#!/usr/bin/env bash
# 用固定提交的 hydrogen 源码重建 shio 内核产物，并同步到 app/src/main/jniLibs。
#
# 行为与 scripts/sync-shio.ps1 对齐：
#   1. 校验 hydrogen HEAD 等于脚本内固定的提交；
#   2. 用 cargo-ndk + 本机 NDK 交叉编译 shio-ffi（release）；
#   3. 复制 libshio_ffi.so 到 jniLibs/<abi>/，同时同步 C ABI 头文件；
#   4. 用 NDK 自带 llvm-readelf 校验 ELF 架构与 16 KB page 对齐。
#
# 脚本不修改 hydrogen 仓库（不 checkout、不 commit）。
#
# 用法：
#   scripts/sync-shio.sh [--hydrogen <path>] [--ndk <version>] [--skip-build]
set -euo pipefail

# 固定的内核来源与 ABI 基线（改动需同步 .design/DECISIONS.log）。
PINNED_COMMIT="5f3e8e324cda0bf772e21ad7bcd2036435ebcdd4"
DEFAULT_NDK_VERSION="29.0.14206865"
PAGE_ALIGN="0x4000" # 16 KB

HYDROGEN_REPO=""
NDK_VERSION="$DEFAULT_NDK_VERSION"
SKIP_BUILD=0

usage() {
    cat <<'EOF'
用法：scripts/sync-shio.sh [--hydrogen <path>] [--ndk <version>] [--skip-build]

  --hydrogen <path>  hydrogen 仓库路径，默认与 oxygen 同级
  --ndk <version>    NDK 版本，默认 29.0.14206865
  --skip-build       只复制并校验 target 下已有产物
  -h, --help         显示帮助

依赖（首次使用）：
  (cd ../hydrogen && rustup target add aarch64-linux-android x86_64-linux-android)
  cargo install cargo-ndk
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --hydrogen)
            [[ $# -ge 2 ]] || { echo "缺少 --hydrogen 的参数" >&2; exit 2; }
            HYDROGEN_REPO="$2"
            shift 2
            ;;
        --ndk)
            [[ $# -ge 2 ]] || { echo "缺少 --ndk 的参数" >&2; exit 2; }
            NDK_VERSION="$2"
            shift 2
            ;;
        --skip-build)
            SKIP_BUILD=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "未知参数：$1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

step() {
    printf '==> %s\n' "$*"
}

die() {
    printf '错误：%s\n' "$*" >&2
    exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "$HYDROGEN_REPO" ]]; then
    HYDROGEN_REPO="$(cd "$repo_root/../hydrogen" 2>/dev/null && pwd)" ||
        die "找不到同级 hydrogen 仓库，请用 --hydrogen 指定"
fi
[[ -d "$HYDROGEN_REPO/.git" ]] || die "hydrogen 仓库路径无效：$HYDROGEN_REPO"

jni_libs_root="$repo_root/app/src/main/jniLibs"
header_src="$HYDROGEN_REPO/crates/shio-ffi/include/shio.h"
header_dst="$repo_root/app/src/main/cpp/include/shio.h"

sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ndk_root="$sdk_dir/ndk/$NDK_VERSION"
[[ -d "$ndk_root" ]] || die "未安装 NDK $NDK_VERSION：$ndk_root"

readelf="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
[[ -x "$readelf" ]] || die "NDK 中缺少 llvm-readelf：$readelf"

# --- 1. 固定提交校验 -------------------------------------------------------
step "校验 hydrogen HEAD"
head_commit="$(git -C "$HYDROGEN_REPO" rev-parse HEAD)" ||
    die "git rev-parse 失败"
if [[ "$head_commit" != "$PINNED_COMMIT" ]]; then
    cat >&2 <<EOF
hydrogen HEAD ($head_commit) 与脚本固定的提交 ($PINNED_COMMIT) 不一致。
请先让 hydrogen 处于该提交（例如 git -C "$HYDROGEN_REPO" switch --detach $PINNED_COMMIT），
或确认来源变更后同时更新 scripts/sync-shio.sh 与 .design/DECISIONS.log。
EOF
    exit 1
fi
if [[ -n "$(git -C "$HYDROGEN_REPO" status --porcelain)" ]]; then
    printf '警告：hydrogen 工作区有未提交改动，产物可能不来自固定提交。\n' >&2
fi

# --- 2. 构建 ---------------------------------------------------------------
find_cargo_ndk() {
    if command -v cargo-ndk >/dev/null 2>&1; then
        command -v cargo-ndk
        return 0
    fi
    local candidate
    for candidate in \
        "${CARGO_HOME:-$HOME/.cargo}/bin/cargo-ndk" \
        "$HOME/.cargo/bin/cargo-ndk"; do
        if [[ -x "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    return 1
}

if [[ "$SKIP_BUILD" -eq 1 ]]; then
    step "跳过构建，使用 target 下现有产物"
else
    step "使用 NDK $NDK_VERSION 构建 shio-ffi"
    export ANDROID_NDK_HOME="$ndk_root"
    export ANDROID_NDK_ROOT="$ndk_root"

    # 显式要求 16 KB page 对齐；NDK r29 默认已是 16 KB，这里固定住避免回退。
    page_flag='-C link-arg=-Wl,-z,max-page-size=16384'
    if [[ -n "${RUSTFLAGS:-}" ]]; then
        if [[ "$RUSTFLAGS" != *"max-page-size=16384"* ]]; then
            export RUSTFLAGS="$RUSTFLAGS $page_flag"
        fi
    else
        export RUSTFLAGS="$page_flag"
    fi

    cargo_ndk="$(find_cargo_ndk)" ||
        die "缺少 cargo-ndk：请先执行 cargo install cargo-ndk（安装后可能在 ~/.cargo/bin）"
    printf '    使用 %s\n' "$cargo_ndk"

    # cargo-ndk 只允许通过 `cargo ndk` 调用；把它所在目录加进 PATH。
    cargo_ndk_dir="$(dirname "$cargo_ndk")"
    (
        cd "$HYDROGEN_REPO"
        PATH="$cargo_ndk_dir:$PATH" cargo ndk -t arm64-v8a -t x86_64 build --release -p shio-ffi
    ) || die "cargo ndk 构建失败"
fi

# --- 3. 复制头文件 ---------------------------------------------------------
[[ -f "$header_src" ]] || die "缺少头文件：$header_src"
mkdir -p "$(dirname "$header_dst")"
install -m 0644 "$header_src" "$header_dst"
step "同步 C ABI 头文件 -> $header_dst"
printf '    sha256=%s\n' "$(sha256sum "$header_dst" | awk '{print $1}')"

# --- 4. 复制产物并校验 -----------------------------------------------------
check_abi() {
    local abi="$1" triple="$2" expected_machine="$3"
    local source="$HYDROGEN_REPO/target/$triple/release/libshio_ffi.so"
    [[ -f "$source" ]] || die "缺少产物：$source（去掉 --skip-build 重新构建）"

    local dest_dir="$jni_libs_root/$abi"
    local dest="$dest_dir/libshio_ffi.so"
    mkdir -p "$dest_dir"
    install -m 0644 "$source" "$dest"
    step "复制 $abi -> $dest"

    if ! "$readelf" -h "$dest" | grep -qF "$expected_machine"; then
        die "$abi 的 ELF 架构不符：期望 $expected_machine"
    fi

    local loads
    loads="$("$readelf" -lW "$dest" | awk '$1 == "LOAD" {print $NF}')"
    [[ -n "$loads" ]] || die "$abi 没有 LOAD 段"
    local align
    while read -r align; do
        if [[ "$align" != "$PAGE_ALIGN" ]]; then
            die "$abi 的 LOAD 段 page 对齐为 $align，期望 $PAGE_ALIGN（16 KB）"
        fi
    done <<< "$loads"

    local hash size
    hash="$(sha256sum "$dest" | awk '{print $1}')"
    size="$(stat -c '%s' "$dest")"
    printf '   机器=%s 对齐=%s 大小=%s sha256=%s\n' \
        "$expected_machine" "$PAGE_ALIGN" "$size" "$hash"
}

check_abi "arm64-v8a" "aarch64-linux-android" "AArch64"
check_abi "x86_64" "x86_64-linux-android" "Advanced Micro Devices X86-64"

# --- 5. 同步 BuildConfig 里的内核提交 -------------------------------------
# 关于页展示的「内核提交」来自 BuildConfig；与产物同步改写，避免手改遗漏造成溯源失真。
gradle_file="$repo_root/app/build.gradle.kts"
if [[ -f "$gradle_file" ]]; then
    # 只改 SHIO_KERNEL_COMMIT 下一行里的 40 位提交哈希（源码中是 "\"<hash>\"" 形式）。
    tmp_file="$(mktemp)"
    awk -v commit="$PINNED_COMMIT" '
        prev ~ /"SHIO_KERNEL_COMMIT",/ { gsub(/[0-9a-f]{40}/, commit) }
        { print; prev = $0 }
    ' "$gradle_file" > "$tmp_file"
    if ! cmp -s "$tmp_file" "$gradle_file"; then
        install -m 0644 "$tmp_file" "$gradle_file"
        step "更新 app/build.gradle.kts 的 SHIO_KERNEL_COMMIT -> $PINNED_COMMIT"
    else
        step "SHIO_KERNEL_COMMIT 已是 $PINNED_COMMIT"
    fi
    rm -f "$tmp_file"
fi

step "完成：产物来自 hydrogen $PINNED_COMMIT（NDK $NDK_VERSION）"
