#!/usr/bin/env bash
set -euo pipefail

mkdir -p screenshots
adb logcat -c

capture_logs() {
  adb logcat -d > screenshots/logcat.txt || true
}
trap capture_logs EXIT

adb install -r android/app/build/outputs/apk/release/app-release.apk
adb shell monkey -p app.nitronbox.mobile -c android.intent.category.LAUNCHER 1
sleep 8

if ! adb shell pidof app.nitronbox.mobile > screenshots/pid.txt; then
  grep -E "AndroidRuntime|FATAL EXCEPTION|ReactNativeJS|app.nitronbox.mobile" screenshots/logcat.txt || true
  exit 1
fi

adb shell uiautomator dump /sdcard/startup.xml
adb pull /sdcard/startup.xml screenshots/startup.xml
grep -q "NitronBox" screenshots/startup.xml

curl -Ls "https://get.maestro.mobile.dev" | bash
"$HOME/.maestro/bin/maestro" test .maestro/visual.yaml
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml screenshots/window.xml
