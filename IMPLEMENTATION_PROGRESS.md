# Lazure — implementation progress

Updated: 2026-11-07

## Product constraints

- Keep the application name **Lazure** and application ID `ru.myessentiality.essential`.
- Extend the existing Compose, Material 3, Hilt, Room, DataStore and LiteRT-LM architecture. Preserve the current Mont typography and cyan/neutral color kit unless a small token change is needed for accessible contrast.
- Never substitute mock transcript text or speakers for model inference. The app must disclose missing local models.
- All speech and language inference must remain on-device. INTERNET is permitted only for optional local model downloads; the redesigned AI chat does not expose cloud inference.

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
| 5. AI models | Partial | Restored the LiteRT-LM preset catalog with resumable download, selection and removal. ASR/diarization models and engines are still missing. |
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

## Frontend consistency update — 2026-11-07

Latest requested UI adjustment: Live now uses the same neutral card treatment as the other Home actions; the global microphone FAB has been removed. The static light/dark Material 3 palettes explicitly define primary, secondary, tertiary, container, inverse and surface roles around #00CED1 and turquoise shades, removing inherited default purple roles. Android Dynamic Color continues to follow the system palette when enabled. `./gradlew :app:assembleDebug` passed after this adjustment; no device visual check was performed.

**Status: Completed for this design pass; overall product remains partial.**

- Removed Spanish locale resources and its Settings option; RU and EN remain.
- Removed the old visible Essential logo treatment from splash and the retained notes chat placeholder. The launcher name and product wordmark remain Lazure; package/application ID stay stable for upgrade compatibility.
- Made Android Dynamic Color the default and consolidated Material 3 shapes with larger 12–32 dp rounded tokens. Home action cards, settings surfaces, chat bubbles, input and the floating bottom navigation now use the same palette and shape system.
- Reworked the AI chat into a local-only conversation screen with an empty state, prompt chips, rounded message cards and a direct link to model selection. Chat requests force local mode.
- Returned the existing downloadable LiteRT-LM catalog in place of manual `.litertlm` file import on the model screen. Fixed model catalog options so downloadable presets expose their install action. Selection, resumable downloads and deletion remain backed by the existing downloader.
- Added the Android INTERNET permission only because catalog downloads need it. Inference remains local; documentation now records this boundary.

**Verification:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed; the current unit-test XML reports 44 tests, 0 failures/errors. The merged debug manifest contains INTERNET. Emulator launch was attempted, but this host has no connected device and the `processing-phone` AVD exits with `Incompatible processor ... requires ... neon`; this pass was not visually exercised on-device.

**Next:** Resume concrete offline ASR and diarization implementation; then exercise the UI on an ARM64 device/emulator and validate model download and microphone flows. ASR, diarization, recording-scoped summary and RAG remain incomplete.

## Settings and PIN update — 2026-11-07

- Removed Account/subscription and Privacy/Policy/Terms blocks from Settings, including the account refresh effect.
- Changed user-facing storage/repository naming to Storage in onboarding, settings and Home/navigation. Internal database paths and repository class names stay compatible.
- Added `PinRepository` with DataStore persistence, a random salt and HMAC-SHA256 verifier using a non-exportable Android Keystore key. The PIN itself is never persisted. Five incorrect attempts impose a persisted 30-second cooldown.
- Added Material 3 PIN setup/confirmation (4 or 6 ASCII digits), unlock, and disabling with the existing PIN. `PinGuard` blocks initial content until settings load and authentication succeeds, and locks again on activity stop. Screen contents are protected against screenshots/task snapshots while PIN is enabled. PIN is an application access lock, not file encryption.
- PIN controls and dialogs use the shared typography, rounded shapes and theme colors. Legacy chrome now uses the active Material color scheme for dark mode as well as light mode.
- Added repository tests for both PIN lengths, persistence without plaintext, rejection of wrong codes, disabling, overwrite prevention, cooldown and failure reset. Device validation of Android Keystore and lifecycle/UI remains required.

Verification for this update: `./gradlew :app:testDebugUnitTest :app:assembleDebug` passed (50 unit tests, including 6 PIN tests; 0 failures/errors). The repository tests use a test HMAC key and a real temporary DataStore; they do not establish Android Keystore/device behavior. No new device visual or lifecycle test is claimed.
