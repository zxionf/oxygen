BT=~/Android/Sdk/build-tools/36.1.0
APK=app/build/outputs/apk/release/app-release.apk
$BT/apksigner verify --print-certs $APK | head -5     # 证书应是 zxionf
$BT/zipalign -c -P 16 -v 4 $APK | tail -2             # 16 KB 页对齐
