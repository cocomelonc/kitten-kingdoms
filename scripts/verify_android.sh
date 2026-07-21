#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"

./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
BUILD_TOOLS="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
AAPT="$BUILD_TOOLS/aapt"
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"

test -f "$APK"
test -x "$AAPT"
test -x "$APKSIGNER"
test -x "$ZIPALIGN"

BADGING="$($AAPT dump badging "$APK")"
grep -q "sdkVersion:'33'" <<<"$BADGING"
grep -q "targetSdkVersion:'36'" <<<"$BADGING"
grep -q "package: name='com.cocomelonc.kittenkingdoms.debug'" <<<"$BADGING"

if unzip -Z1 "$APK" | grep -Eq '^lib/.+\.so$'; then
    echo "Unexpected native library found in APK" >&2
    exit 1
fi

"$APKSIGNER" verify --verbose "$APK"
"$ZIPALIGN" -c -P 16 -v 4 "$APK"

echo "Verified: minSdk 33, targetSdk 36, signature, 16 KB ZIP alignment, no native libraries."
