# Методика и результаты тестирования

## 2026-11-07

- Baseline `./gradlew :app:assembleDebug`: passed before edits.
- Updated `./gradlew :app:assembleDebug`: passed after Room migration, Home/Vault shell, filters/sorting, transcript/speaker editing, model catalog and microphone capture UI changes.
- Updated `./gradlew :app:testDebugUnitTest`: passed, 44 tests.
- Updated `./gradlew :app:connectedDebugAndroidTest`: passed, 4 tests on `processing-phone` API 33 emulator. Covers app package and Room migration/data preservation through 7 → 8.
- Debug APK installed, and `adb shell am start -n ru.myessentiality.essential/com.rassvet.essential.MainActivity` opened the application. Audio import and microphone capture were not exercised end to end with real media.
- Added `TranscriptExportTest` for exact competition keys, empty export, and invalid/out-of-order timestamps.
- No UI automation for the new Home/Vault/Live screens, no model inference, and no WER/DER/RTF measurement was performed.

## UI/UX polish — latest verification, 2026-11-07

- `./gradlew :app:assembleDebug :app:testDebugUnitTest`: passed, **59 tests**, 0 failures/errors. Seven Compose UI tests cover Home/navigation in light/dark at 320 dp with fontScale 1.0/1.6, large-font chat headers and settings. ColorContrastTest checks seven static-theme text pairs at >= 4.5:1.
- `./gradlew :app:connectedDebugAndroidTest`: passed, **9 tests**, 0 failures on ARM64 processing-phone / Android 13 API 33. Four actual Compose viewport cases and a Hilt-backed MainActivity smoke test cover existing Home, Archive, Chat, Settings, PIN setup, Models and Live screens. Existing DB migrations/package tests remain green.
- Native screenshots and visual review: `docs/ui-polish/`; full scope and changed files: `docs/UI_POLISH_REPORT.md`.
- Font scaling is applied to Compose density in tests; this is not a claim that every physical-device font/display setting has been tested. Tests open the recording screen but do not record microphone audio or benchmark ASR.
