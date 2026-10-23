#!/usr/bin/env bash
# P4 验收：冒烟、SHIO_LOCKED、进程被杀后的持久化。
# 用法：scripts/acceptance.sh [serial]
set -euo pipefail

cd "$(dirname "$0")/.."
serial="${1:-}"
adb_cmd=(adb)
if [[ -n "$serial" ]]; then
    adb_cmd+=( -s "$serial" )
fi

pkg="top.zxionf.shio.debug"
smoke_component="$pkg/$pkg.SmokeActivity"
accept_component="$pkg/$pkg.AcceptanceActivity"

run_activity() {
    "${adb_cmd[@]}" shell am start -W -n "$1" ${2:+--es mode "$2"} >/dev/null
}

# 等待某个 logcat 标签出现匹配行；成功时输出该行。
wait_log() {
    local tag="$1" pattern="$2" timeout="${3:-30}" line=""
    for _ in $(seq 1 "$timeout"); do
        line="$("${adb_cmd[@]}" logcat -d -s "$tag":I '*:S' \
            | grep -E "$pattern" | tail -n 1 || true)"
        if [[ -n "$line" ]]; then
            printf '%s\n' "$line"
            return 0
        fi
        sleep 1
    done
    return 1
}

fail() {
    echo "acceptance failed: $*" >&2
    "${adb_cmd[@]}" logcat -d -s ShioSmoke:I ShioAcceptance:I AndroidRuntime:E '*:S' | tail -n 40 >&2
    exit 1
}

echo "==> build + install"
./gradlew --console=plain :app:assembleDebug
"${adb_cmd[@]}" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
"${adb_cmd[@]}" shell am force-stop "$pkg" >/dev/null 2>&1 || true

echo "==> APK native libs"
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E 'lib/(arm64-v8a|x86_64)/libshio_(ffi|jni)\.so'

echo "==> smoke: open/insert/get/list/stats/CLOSED/INVALID_KEY/reopen"
"${adb_cmd[@]}" logcat -c
run_activity "$smoke_component"
smoke="$(wait_log ShioSmoke 'SMOKE (OK|FAIL)' || true)"
[[ -n "$smoke" ]] || fail "smoke timed out"
echo "$smoke"
[[ "$smoke" == *"SMOKE OK"* ]] || fail "smoke reported failure"

echo "==> lock: second handle on the same database must return SHIO_LOCKED"
"${adb_cmd[@]}" logcat -c
run_activity "$accept_component" "lock"
lock="$(wait_log ShioAcceptance 'ACCEPT (OK|FAIL) mode=lock' || true)"
[[ -n "$lock" ]] || fail "lock probe timed out"
echo "$lock"
[[ "$lock" == *"ACCEPT OK mode=lock"* ]] || fail "lock probe reported failure"

echo "==> persistence: write, kill process, verify"
"${adb_cmd[@]}" logcat -c
run_activity "$accept_component" "persist-write"
write="$(wait_log ShioAcceptance 'ACCEPT (OK|FAIL) mode=persist-write' || true)"
[[ -n "$write" ]] || fail "persist-write timed out"
echo "$write"
[[ "$write" == *"ACCEPT OK mode=persist-write"* ]] || fail "persist-write reported failure"

"${adb_cmd[@]}" shell am force-stop "$pkg"
sleep 1
"${adb_cmd[@]}" logcat -c
run_activity "$accept_component" "persist-verify"
verify="$(wait_log ShioAcceptance 'ACCEPT (OK|FAIL) mode=persist-verify' || true)"
[[ -n "$verify" ]] || fail "persist-verify timed out"
echo "$verify"
[[ "$verify" == *"ACCEPT OK mode=persist-verify"* ]] || fail "persist-verify reported failure"

echo "==> acceptance passed"
