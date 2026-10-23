#!/usr/bin/env bash
# 在已连接的设备/模拟器上跑 JNI 冒烟（debug 变体）。
# 用法：scripts/smoke.sh [serial]
set -euo pipefail

cd "$(dirname "$0")/.."
serial="${1:-}"
adb_cmd=(adb)
if [[ -n "$serial" ]]; then
    adb_cmd+=( -s "$serial" )
fi

"${adb_cmd[@]}" shell am force-stop top.zxionf.shio.debug >/dev/null 2>&1 || true
"${adb_cmd[@]}" logcat -c

echo "==> assembleDebug + install"
./gradlew --console=plain :app:assembleDebug
"${adb_cmd[@]}" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null

echo "==> run smoke"
"${adb_cmd[@]}" shell am start -W -n \
    top.zxionf.shio.debug/top.zxionf.shio.debug.SmokeActivity >/dev/null

result=""
for _ in $(seq 1 30); do
    result="$("${adb_cmd[@]}" logcat -d -s ShioSmoke:I '*:S' | grep -E 'SMOKE (OK|FAIL)' | tail -n 1 || true)"
    [[ -n "$result" ]] && break
    sleep 1
done

if [[ -z "$result" ]]; then
    echo "smoke timed out; recent logcat:" >&2
    "${adb_cmd[@]}" logcat -d -s ShioSmoke:I AndroidRuntime:E '*:S' | tail -n 40 >&2
    exit 1
fi

echo "$result"
[[ "$result" == *"SMOKE OK"* ]]
