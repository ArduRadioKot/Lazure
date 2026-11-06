# Ограничения решения

## Поддерживаемые условия

- Audio import via Android document picker; audio copy remains in application-private storage.
- Audio playback from the copied file, Room metadata persistence, deletion, and exact-shape JSON export (empty transcript until inference exists).

## Известные ограничения

- No offline ASR runtime/model or speaker diarization engine/model is currently included. Automatic transcription, diarization, timestamps, recording, transcript editing, summaries, and recording-scoped chat are not implemented.
- There is no audio capture flow or foreground recording service yet.
- JSON export is structurally valid for an unprocessed recording, but it has no transcript segments and is not a competition-ready transcription result.
- Instrumentation tests and app installation/start were exercised on the `processing-phone` ARM64 emulator. Migration was checked against preserved legacy table names, but not against a copy of a real user's database. Import/playback were not exercised end to end with a sample recording.
- `MediaMetadataRetriever` duration and audio playback depend on Android platform codec support. Import reports a clear error when no readable audio track is available.

## Что не входит в текущую версию

## Риски демонстрации

## Дальнейшее развитие

