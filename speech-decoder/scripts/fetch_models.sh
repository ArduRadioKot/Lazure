#!/usr/bin/env bash
# Downloads every model in models/manifest.json and the sherpa-onnx runtime into the Lazure Android app.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; APP="$ROOT/src/mobile/android/src/main"
T="$(mktemp -d)"; B=https://github.com/k2-fsa/sherpa-onnx/releases/download
mkdir -p "$APP/assets/models"/{vad,asr-gigaam-v3-ru,diar-segmentation,diar-embedding}
curl -L -o "$APP/assets/models/vad/silero_vad.onnx" $B/asr-models/silero_vad.onnx
curl -L $B/asr-models/sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16.tar.bz2 | tar xj -C "$T"
cp "$T"/sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16/{model.int8.onnx,tokens.txt,LICENSE} "$APP/assets/models/asr-gigaam-v3-ru/"
curl -L $B/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2 | tar xj -C "$T"
cp "$T"/sherpa-onnx-pyannote-segmentation-3-0/{model.int8.onnx,LICENSE} "$APP/assets/models/diar-segmentation/"
curl -L -o "$APP/assets/models/diar-embedding/model.onnx" $B/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx
# static-link build: ORT is inside the JNI lib, so it does not clash with onnxruntime-android used for DTLN
curl -L $B/v2.0.1/sherpa-onnx-v2.0.1-android-static-link-onnxruntime.tar.bz2 | tar xj -C "$T"
for a in arm64-v8a x86_64; do mkdir -p "$APP/jniLibs/$a"; cp "$T/jniLibs/$a/libsherpa-onnx-jni.so" "$APP/jniLibs/$a/"; done
mkdir -p "$APP/assets/models/denoise"
for n in 1 2; do curl -L -o "$APP/assets/models/denoise/dtln_$n.onnx" https://huggingface.co/niobures/DTLN/resolve/main/models/DTLN/onnx/model_$n.onnx; done
# Bundled LLM (Qwen3 0.6B, 614 MB) copied to the model dir on first launch
mkdir -p "$APP/assets/bundled_llm"
# Pinned: litert-community re-exported its models on 2026-10-11 for LiteRT-LM 0.18 (chat-template "content parts"); the
# 0.12 runtime used by the app drops the user turn from those files. This is the June INT4 no-think build (347 MB).
curl -L -o "$APP/assets/bundled_llm/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm" https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/6aa2daf8ab/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm
# Separation model is produced locally (needs python + torch): see scripts/export_mossformer2.py, then place
#   mossformer2_ss_16k_int8.onnx in $APP/assets/models/separation/
mkdir -p "$APP/assets/models/separation"
rm -rf "$T"; echo done
