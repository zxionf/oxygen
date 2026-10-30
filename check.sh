#!/usr/bin/env bash
# release 产物自检：签名证书、16 KB 页对齐、版本号与 gradle/version.properties 一致。
set -u
BT=~/Android/Sdk/build-tools/36.1.0
APK=shio-vlt/build/outputs/apk/release/shio-vlt-release.apk
VERSION_FILE=gradle/version.properties

echo "== 签名证书（应为 zxionf）=="
$BT/apksigner verify --print-certs "$APK" | head -5

echo "== 16 KB 页对齐 =="
$BT/zipalign -c -P 16 -v 4 "$APK" | tail -2

echo "== 版本号（应与 $VERSION_FILE 一致）=="
want_code=$(grep -E '^versionCode=' "$VERSION_FILE" | cut -d= -f2 | tr -d '[:space:]')
want_name=$(grep -E '^versionName=' "$VERSION_FILE" | cut -d= -f2 | tr -d '[:space:]')
got=$( "$BT/aapt2" dump badging "$APK" | head -1 )
got_code=$(printf '%s' "$got" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")
got_name=$(printf '%s' "$got" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
echo "version.properties: $want_code / $want_name"
echo "release APK:        $got_code / $got_name"
if [ "$want_code" != "$got_code" ] || [ "$want_name" != "$got_name" ]; then
  echo "版本号不一致：先重新构建 :shio-vlt:assembleRelease" >&2
  exit 1
fi
echo "版本号一致"
