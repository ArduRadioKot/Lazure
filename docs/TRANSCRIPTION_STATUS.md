# Lazure transcription and frontend status

Updated 2026-11-07. The application remains **Lazure** and keeps its existing cyan/neutral Material 3 design system.

## Verified repository state

- Single Android module :app, source at src/mobile/android; Compose, Material 3, Navigation Compose, Hilt, Room, DataStore and LiteRT-LM are present.
- Entry: MainActivity → EssentialApp. Existing markdown notes, chat history, FTS/index, settings, theme and local LLM paths are preserved.
- Room is currently v8. Migrations through 7→8 preserve existing note/chat data while adding recording, transcript, speakers, summaries and recording chat-session structures.
- Existing audio path supports SAF import, app-private file copies, duration metadata, playback and exact-schema JSON. No ASR or diarization inference implementation/model was bundled when audited.
- The merged app manifest removes INTERNET; no cloud speech or text inference was added.

## Completed implementation

- Room v8 recording metadata, speaker/summary/chat-session tables, non-destructive migration and transcript validation/export contracts.
- Processing states and interfaces for decoder, preprocessor, VAD, ASR, diarization, alignment, recorder, model repository and coordinator. There is not yet a concrete ASR/diarization engine implementation.
- Home dashboard with four actions, recent Room-backed recordings, four-section bottom navigation and an entry to the retained notes workspace. Existing Mont font and cyan/neutral Material 3 tokens are retained.
- Vault with local SAF import, search, processing state display, audio playback and JSON export.
- Microphone screen with runtime permission request, AAC/M4A local recording, timer, pause/resume on API 24+, stop, Room registration and Vault handoff. The unfinished recording is currently tied to the screen lifecycle, so this is not yet reliable background capture.
- Model catalog enumerates verified local .litertlm files and supports import/select/remove; ASR and diarization sections disclose their missing model state rather than showing fake entries.

## ASR / diarization candidate review

Sherpa-ONNX is a plausible offline runtime candidate for evaluation because its official Android documentation covers Kotlin/native Android integration and its project documents offline ASR and speaker diarization. This is a candidate only: the current app does not include or call Sherpa-ONNX. See the [official Android build guide](https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html), [offline ASR overview](https://k2-fsa.github.io/sherpa/onnx/) and [speaker diarization API](https://k2-fsa.github.io/sherpa/onnx/c-api/html/speaker_diarization.html).

A Russian small Zipformer model is listed by the project; its model page documents an int8 encoder around 24 MB plus decoder/joiner/tokens, but model size does not establish acceptable Russian meeting accuracy. The model is derived from Vosk small-ru; Vosk publishes the source model and its own dataset-specific scores on the [official model page](https://alphacephei.com/vosk/models). Those scores are not Lazure evaluation results.

A diarization option documented in the Sherpa ecosystem uses pyannote segmentation and speaker embeddings with clustering. Runtime, native ABI packaging, model redistribution terms, compatibility with Russian meeting speech, memory and accuracy still need validation. Do not ship these artifacts until license notices and APK size are checked.

## Not yet implemented / known limits

- No real offline speech inference, automatic diarization or model-derived timestamps. JSON for an unprocessed recording has an empty transcript array, which is valid JSON but not a competition-ready result.
- No foreground recording service, persistent notification or recovery after process death. A capture interrupted before stop/save may be lost.
- Vault now supports rename/delete confirmation, date/title sorting, transcript editing while preserving originalText, speaker display-name edits, and seeking to an existing segment. Retry/reprocess, waveform/current-segment highlighting and recording-specific Summary/Ask AI actions remain. Current transcript rows are only shown if a real pipeline writes segments; no placeholder transcript is fabricated.
- Local summary generation and recording-scoped AI chat/RAG/citations remain unimplemented. The AI tab currently reuses the existing general local chat.
- Recording import, playback and microphone capture have not yet been exercised with a real audio file/microphone during this session.

## Checks performed

- ./gradlew :app:assembleDebug — passed; builds ARM64 and x86_64 native targets for the debug APK.
- ./gradlew :app:testDebugUnitTest — passed, 44 tests.
- ./gradlew :app:connectedDebugAndroidTest — passed, 4 tests on the processing-phone API 33 emulator.
- ./gradlew :app:installDebug — installed successfully. ADB reported ru.myessentiality.essential/com.rassvet.essential.MainActivity as the resumed activity.
- No device microphone or sample-file E2E test was performed; no ASR/DER/WER/RTF numbers are claimed.

## Next work

1. Pick a model/runtime only after validating redistribution licenses and ARM64 packaging; implement real decoder/resampling/VAD/ASR/diarization/alignment and run real inference.
2. Move capture to a foreground service with notification and recovery; test microphone and SAF import end to end.
3. Complete Vault editing/actions, then summary and recording-scoped RAG using existing LiteRT-LM and local retrieval infrastructure.
