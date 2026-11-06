#!/usr/bin/env bash
set -euo pipefail

AVD_NAME="${1:-processing-phone}"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

if [[ -z "$SDK_DIR" && -f local.properties ]]; then
    SDK_DIR="$(sed -n 's/^sdk\.dir=//p' local.properties | head -n 1 | sed 's/\\:/\:/g; s/\\\\/\\/g')"
fi

if [[ -z "$SDK_DIR" || ! -x "$SDK_DIR/platform-tools/adb" || ! -x "$SDK_DIR/emulator/emulator" ]]; then
    echo "Android SDK не найден. Укажите ANDROID_SDK_ROOT или ANDROID_HOME." >&2
    exit 1
fi

ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"

if ! "$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { found = 1 } END { exit !found }'; then
    if ! "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME"; then
        echo "AVD '$AVD_NAME' не найден. Создайте его в Android Studio Device Manager." >&2
        exit 1
    fi
    "$EMULATOR" -avd "$AVD_NAME" >/dev/null 2>&1 &
fi

"$ADB" wait-for-device
until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
done

./gradlew :app:installDebug
"$ADB" shell monkey -p ru.myessentiality.essential 1
