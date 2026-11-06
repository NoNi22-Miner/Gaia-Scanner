#!/usr/bin/env bash
# Runs the plain-Kotlin unit tests (app/src/test/.../core) without the Android build.
# For sandboxes that cannot download the Android toolchain. It uses the Kotlin
# compiler and JUnit jars that ship inside a local Gradle install.
# Usage: scripts/core-tests-local.sh [gradle lib dir, default /opt/gradle/lib]
set -euo pipefail

LIB="${1:-/opt/gradle/lib}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

jar() { ls "$LIB"/$1 2>/dev/null | head -1; }
CCP="$(jar 'kotlin-compiler-embeddable-*.jar'):$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'kotlin-script-runtime-*.jar'):$(jar 'kotlin-reflect-*.jar'):$(jar 'kotlinx-coroutines-core-jvm-*.jar'):$(jar 'trove4j-*.jar'):$(jar 'annotations-*.jar'):$(jar 'kotlin-daemon-embeddable-*.jar')"
TCP="$(jar 'kotlin-stdlib-[0-9]*.jar'):$(jar 'junit-4*.jar'):$(jar 'hamcrest-core-*.jar')"

MAIN="$ROOT/app/src/main/java/com/gaiascanner/app/core"
TEST="$ROOT/app/src/test/java/com/gaiascanner/app/core"

java -cp "$CCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -jvm-target 17 \
  -cp "$TCP" -d "$OUT" "$MAIN"/*.kt "$TEST"/*.kt

CLASSES=$(cd "$TEST" && ls *Test.kt | sed 's/\.kt$//' | sed 's/^/com.gaiascanner.app.core./')
java -cp "$OUT:$TCP" org.junit.runner.JUnitCore $CLASSES
