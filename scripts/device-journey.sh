#!/usr/bin/env bash
set -euo pipefail
mkdir -p ci-artifacts
adb wait-for-device
prepare() {
  adb shell settings put system screen_off_timeout 1800000
  adb shell svc power stayon true
  adb shell input keyevent 224
  adb shell input keyevent 82
}
collect() {
  rc=$?
  trap - EXIT
  set +e
  adb shell settings put system font_scale 1.0
  adb shell dumpsys package com.jonkryl.binarygarden > ci-artifacts/package.txt
  adb logcat -d -v threadtime > ci-artifacts/logcat.txt
  adb exec-out screencap -p > ci-artifacts/device-screen.png
  exit "$rc"
}
trap collect EXIT
prepare
adb install -r ci-apks/debug/app-debug.apk
adb install -r ci-apks/androidTest/debug/app-debug-androidTest.apk
adb shell pm clear com.jonkryl.binarygarden
run_tests() {
  name="$1"
  class="$2"
  prepare
  adb shell am instrument -w -r -e class "$class" com.jonkryl.binarygarden.test/androidx.test.runner.AndroidJUnitRunner | tee "ci-artifacts/$name.txt"
  python3 scripts/check-instrumentation.py "ci-artifacts/$name.txt"
}
run_tests 01-journey com.jonkryl.binarygarden.GardenJourneyTest
adb shell am force-stop com.jonkryl.binarygarden
run_tests 02-real-process-restart com.jonkryl.binarygarden.RestartTest
adb shell settings put system font_scale 2.0
adb shell am force-stop com.jonkryl.binarygarden
run_tests 03-large-font com.jonkryl.binarygarden.LargeFontTest
adb shell settings put system font_scale 1.0
adb shell am force-stop com.jonkryl.binarygarden
adb shell am start -W -n com.jonkryl.binarygarden/.MainActivity
adb shell uiautomator dump /sdcard/garden-ui.xml
adb pull /sdcard/garden-ui.xml ci-artifacts/garden-ui.xml
adb exec-out screencap -p > ci-artifacts/game-screen.png
printf 'source=%s\nrun=%s\napi=%s\n' "${SOURCE_SHA:-$GITHUB_SHA}" "$GITHUB_RUN_ID" "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" > ci-artifacts/provenance.txt
