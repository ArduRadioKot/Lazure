#!/usr/bin/env bash
# Скачивает onnxruntime-android AAR, кладёт заголовки и libonnxruntime.so куда ждёт CMake.
set -euo pipefail
VER="${1:-1.19.2}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
curl -L -o "$TMP/ort.aar" "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${VER}/onnxruntime-android-${VER}.aar"
unzip -q "$TMP/ort.aar" -d "$TMP/ort"
mkdir -p "$ROOT/app/src/main/cpp/third_party/onnxruntime/headers" "$ROOT/app/src/main/jniLibs/arm64-v8a"
cp -r "$TMP/ort/headers/." "$ROOT/app/src/main/cpp/third_party/onnxruntime/headers/"
cp "$TMP/ort/jni/arm64-v8a/libonnxruntime.so" "$ROOT/app/src/main/jniLibs/arm64-v8a/"
rm -rf "$TMP"
echo "ORT ${VER} готов."
