#!/usr/bin/env bash
# Runs the instrumented tests on a connected emulator and saves the measurements
# that the tests log under the "GaiaMeasure" tag (sizes, timings, format support).
set -u

mkdir -p build/device-logs
adb logcat -c || true
adb logcat -v brief -s GaiaMeasure:I > build/device-logs/measurements.txt &
LOGCAT_PID=$!

./gradlew --stacktrace connectedDebugAndroidTest
STATUS=$?

sleep 2
kill "$LOGCAT_PID" 2>/dev/null || true
adb logcat -d > build/device-logs/logcat-full.txt 2>/dev/null || true

echo "===== GaiaMeasure (API $(adb shell getprop ro.build.version.sdk | tr -d '\r')) ====="
cat build/device-logs/measurements.txt
exit "$STATUS"
