# Архитектура решения

## Контекст

## Компоненты

- Android application module: `:app` (`src/mobile/android`). Existing navigation uses Compose Navigation and the existing Home drawer; audio library is an additional destination.
- Existing notes, chat history and FTS remain in Room database `essential.db`.
- Audio metadata is stored in Room `recordings`; transcript utterances are stored in `transcript_segments`. Version 6 → 7 migration creates only the new tables and indices.
- Imported audio is copied from the SAF `OpenDocument` URI into the app-private `files/recordings` directory. Playback reads that local copy. No network permission or service is used for import or playback.
- `TranscriptExport` validates timestamp order/ranges and emits the competition JSON object. It does not create ASR output.
- `RecordingRepository` is the boundary for import, persistence, deletion and export. An offline recognizer/diarizer can be added behind this boundary once runtime and licensed models are selected.

## Основные технические решения

- Existing UI theme, navigation, and application name are preserved.
- Destructive Room fallback has been removed from debug database creation so an unrecognized migration cannot silently erase a user's local database.
- Processing status `awaiting_asr` is explicit: imported audio is not misrepresented as transcribed.

## Зависимости

## Безопасность и приватность
