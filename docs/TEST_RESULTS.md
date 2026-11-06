# Методика и результаты тестирования

## 2026-11-07

- Baseline `./gradlew :app:assembleDebug`: passed before edits.
- Updated `./gradlew :app:assembleDebug`: passed after Room migration, Home/Vault shell, filters/sorting, transcript/speaker editing, model catalog and microphone capture UI changes.
- Updated `./gradlew :app:testDebugUnitTest`: passed, 44 tests.
- Updated `./gradlew :app:connectedDebugAndroidTest`: passed, 4 tests on `processing-phone` API 33 emulator. Covers app package and Room migration/data preservation through 7 → 8.
- Debug APK installed, and `adb shell am start -n ru.myessentiality.essential/com.rassvet.essential.MainActivity` opened the application. Audio import and microphone capture were not exercised end to end with real media.
- Added `TranscriptExportTest` for exact competition keys, empty export, and invalid/out-of-order timestamps.
- No UI automation for the new Home/Vault/Live screens, no model inference, and no WER/DER/RTF measurement was performed.
