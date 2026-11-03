#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell wm size 1080x2400
adb shell wm density 360
adb shell settings put system system_locales zh-CN
for theme in light dark; do
  if [[ "$theme" == dark ]]; then mode=yes; else mode=no; fi
  adb shell cmd uimode night "$mode"
  adb shell am force-stop org.samc.checkform
  adb shell am start -n org.samc.checkform/org.samc.android.MainActivity
  sleep 15
  adb shell dumpsys activity activities | grep org.samc.checkform
  adb exec-out screencap -p > "screenshots/android-$theme.png"
done
