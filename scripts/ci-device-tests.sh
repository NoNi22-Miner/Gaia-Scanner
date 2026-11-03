#!/usr/bin/env bash
# Runs the instrumented tests on a connected emulator and saves the measurements
# that the tests log under the "GaiaMeasure" tag (sizes, timings, format support).
# Failure details and crash logs are printed too, so they show in the job log.
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

API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "===== GaiaMeasure (API $API) ====="
cat build/device-logs/measurements.txt

if [ "$STATUS" -ne 0 ]; then
  echo "===== Failed tests (API $API) ====="
  python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
for path in glob.glob("app/build/outputs/androidTest-results/connected/**/*.xml", recursive=True):
    try:
        root = ET.parse(path).getroot()
    except Exception as e:
        print(f"{path}: unreadable ({e})")
        continue
    for case in root.iter("testcase"):
        for tag in ("failure", "error"):
            for f in case.findall(tag):
                text = (f.text or f.get("message") or "").strip().splitlines()
                print(f"--- {case.get('classname')}.{case.get('name')} [{tag}]")
                print("\n".join(text[:25]))
EOF
  echo "===== Crash buffer and runtime errors (API $API) ====="
  adb logcat -d -b crash 2>/dev/null | tail -80 || true
  grep -E "AndroidRuntime|TestRunner|FATAL|Instrumentation" build/device-logs/logcat-full.txt | tail -80 || true
fi
exit "$STATUS"
