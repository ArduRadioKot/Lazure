# Lazure — implementation progress

Updated: 2026-11-07

## Product constraints

- Keep the application name **Lazure** and application ID `ru.myessentiality.essential`.
- Extend the existing Compose, Material 3, Hilt, Room, DataStore and LiteRT-LM architecture. Preserve the current Mont typography and cyan/neutral color kit unless a small token change is needed for accessible contrast.
- Never substitute mock transcript text or speakers for model inference. The app must disclose missing local models.
- All speech inference must remain on-device. INTERNET permission stays removed.

## Stage 0 — audit

**Status: Completed.**

- Gradle has one Android application module `:app` at `src/mobile/android`, minSdk 24 / compileSdk 36, Kotlin/Java, Compose Material 3, Navigation Compose, Hilt, Room 2.7, DataStore, LiteRT-LM, CMake/JNI.
- Entry point: `MainActivity` → `EssentialApp`; existing app navigation covers onboarding, note/chat home, graph, editor, and settings.
- Theme already supports light/dark, Android dynamic color, edge-to-edge system bars, Mont and Oddval fonts, and project-specific neutral/cyan tokens.
- Room `essential.db` version 7 holds note indexing, markdown notes, wiki edges, chat sessions/messages, recordings, and transcript segments. Migrations preserve existing data; debug destructive fallback was removed.
- Existing local LLM integration is in `data/llm` and `ChatEngine`; the transcript pipeline does not yet use it.
- Current audio feature only imports/copies local files, stores duration/status, plays/deletes files, and exports structurally valid empty JSON. No mic capture, ASR runtime/assets, diarization, transcript editor, summary or recording RAG exists.
- No ASR or speaker model is bundled in app assets/dependencies. A model/runtime and redistribution license need verification before inference work can be marked complete.
- Baseline `./gradlew :app:assembleDebug` passed on 2026-11-07. Latest connected Android instrumentation passed 3 tests on the ARM64 `processing-phone` emulator; 41 unit tests passed.

## Stages 1–9

| Stage | Status | Current verified state / remaining work |
|---|---|---|
| 1. Architecture and storage | Completed | Room v8 adds recording model selections/updatedAt, speakers, summaries, per-recording chat sessions, and 7→8 migration. Added Room transcript repository, validated segment replacement, export/model/decoder/VAD/ASR/diarization/recorder/coordinator contracts, and tested state transitions. |
| 2. Offline ML pipeline | Blocked / not implemented | Audited Android-compatible offline candidate(s), but no selected runtime/model dependency or licensed weights are bundled yet. Import is real; speech inference is not. |
| 3. Main UI | Partial | Added a four-tab Home / Vault / AI / Settings shell, branded Home dashboard, adaptive action grid, recent Room recordings, direct Import picker, Vault search/filter/sort, rename/delete confirmation, audio playback, transcript segment seeking/editing, speaker renaming and exact-shape JSON export. Missing retry/reprocess, waveform/playback progress, current-segment highlight, and Summary/Ask AI recording actions. Existing Notes workspace is reachable from Home. |
| 4. Live recording | Partial | Microphone permission flow, AAC/M4A capture, pause/resume (API 24+), timer, local file registration in Room and Vault handoff work in the foreground screen. No foreground service or process-death recovery yet; transcription stays unavailable without the speech model. |
| 5. AI models | Partial | Added a model screen that enumerates verified local LiteRT-LM files, imports/selects/removes those files and explicitly reports missing ASR/diarization models. No ASR registry implementation/assets yet. |
| 6. AI Summary | Not implemented | Add persisted summary generation through local LLM after transcript availability. |
| 7. AI Chat and RAG | Not implemented for recordings | Reuse existing ChatEngine and local retrieval; isolate sessions and sources by recording. |
| 8. Polish and performance | Not started | Accessibility, small/wide layouts, offline/error states, memory, long audio. |
| 9. Release readiness | Partial | Build, unit and emulator tests exist; no model license dossier, evaluation set, inference benchmark, or full audio user journey. |

## Next actions

1. Select and integrate an Android ARM64 offline ASR/diarization runtime, acquire/verify redistributable model artifacts and implement a real end-to-end transcript pass. Candidate review is documented in `docs/TRANSCRIPTION_STATUS.md`.
2. Move microphone capture into a foreground service with persistent notification and recovery after lifecycle/process interruption, then exercise capture with a real microphone.
3. Finish Vault actions (rename/delete confirmation, sort and retry/reprocess), transcript editing and speaker metadata controls; then implement persisted summaries and recording-scoped chat/RAG.

## Last verification

- `./gradlew :app:assembleDebug` — passed.
- `./gradlew :app:testDebugUnitTest` — passed, 41 tests.
- `./gradlew :app:connectedDebugAndroidTest` — passed, 3 tests on ARM64 emulator.
- After stage 1: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` passed; Android instrumentation passed 4 tests, including 7→8 table/data preservation.
- Current UI/recording pass: `./gradlew :app:assembleDebug` passed; `./gradlew :app:testDebugUnitTest` passed (44 tests); `./gradlew :app:connectedDebugAndroidTest` passed (4 tests on processing-phone API 33).
- Latest APK installed and opened with `adb shell am start -n ru.myessentiality.essential/com.rassvet.essential.MainActivity`. No real audio import or microphone recording was performed in this verification.

## Stage 3 — main UI report

**Status: Partial.** The app now opens to a branded Lazure workspace using the existing Mont typography, cyan/neutral theme, Material 3 shapes and edge-to-edge handling. Home actions route to local recording, SAF import, model catalog and Vault. The bottom bar contains Home, Vault, AI and Settings. Recent recordings are read from Room. The old notes workspace remains reachable from Home.

Vault supports SAF import, Room-backed search/status filters, date/title sorting, rename and confirmed delete, audio playback, transcript segment seek, text correction with originalText preservation, speaker display-name edits and exact-schema JSON export. Retry/reprocess and waveform/current-segment highlighting remain. AI still uses the existing general ChatScreen; no recording-scoped chat or summary is claimed.

## Stage 4 — live capture report

**Status: Partial.** Live requests `RECORD_AUDIO`, records local AAC in an M4A file, shows elapsed time, supports pause/resume on Android N+ and stop/save, then registers the audio as an `awaiting_asr` Room recording and returns to Vault. The same `recordings/` app-private directory is used by imports and live audio. Capture remains owned by the Compose screen: leaving the screen or process death can interrupt or lose an unfinished capture. A foreground service and on-device microphone exercise are still required.
