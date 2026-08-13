#!/usr/bin/env bash
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

JAVA_HOME=/opt/java11
ANDROID_HOME=/opt/android-sdk/android-sdk
ANDROID_SDK_ROOT=/opt/android-sdk/android-sdk
export JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -f "$JAVA_HOME/bin/java" ]; then
    echo "ERROR: не найден Java: $JAVA_HOME/bin/java" >&2
    exit 1
fi

if [ ! -f "$ANDROID_SDK_ROOT/platforms/android-29/android.jar" ]; then
    echo "ERROR: не найден Android platform 29: $ANDROID_SDK_ROOT/platforms/android-29/android.jar" >&2
    exit 1
fi

if [ ! -f "/data/data/com.termux/files/usr/bin/aapt2" ]; then
    echo "ERROR: не найден рабочий Termux aapt2: /data/data/com.termux/files/usr/bin/aapt2" >&2
    exit 1
fi

if [ ! -f "$ROOT_DIR/gradlew" ]; then
    echo "ERROR: не найден Gradle wrapper: $ROOT_DIR/gradlew" >&2
    exit 1
fi

cd "$ROOT_DIR"
bash ./gradlew assembleDebug

echo "APK: $ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
