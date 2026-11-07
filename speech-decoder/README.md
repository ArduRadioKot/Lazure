# speech-app — каркас пайплайна захвата и транскрибации речи (Android, Kotlin + C++/NDK)

## Пайплайн
```
[Oboe callback, RT]  0 capture → 1 AEC (HAL + AcousticEchoCanceler) → 2 downmix → 3 resample 16k → 4 HPF 80 Hz
        │ SpscRing<float>
[DspWorker, prio -16]  5 DTLN → 6-7 RMS+compressor/limiter → 8 Silero VAD → 9 Chunker (64000, overlap 16000)
        │ ChunkPool (ready/free)
[MlOrchestrator, bg]  [overlap?] 10 MossFormer → 11 GigaAM v3 → ECAPA → кластеризация → 12 merge
        │ один JNI-callback на сегмент
[Kotlin] SharedFlow → ViewModel → UI
```

## Сборка
1. `./scripts/fetch_ort.sh` — положит заголовки ONNX Runtime и `libonnxruntime.so` (arm64-v8a).
2. Положите модели в `app/src/main/assets/models/`:

| Файл | Обязательно | Что это |
|---|---|---|
| `dtln_1.onnx`, `dtln_2.onnx` | да | DTLN (две stateful-модели) |
| `silero_vad.onnx` | да | Silero VAD v5 |
| `gigaam_ctc.onnx`, `vocab.txt` | да | GigaAM v3 CTC и словарь (строка = id, blank = последний индекс) |
| `ecapa.onnx` | да | ECAPA-TDNN, 192-мерный эмбеддинг |
| `mossformer.onnx` | нет | без него разделение не включается |
| `overlap_seg.onnx` | нет | pyannote segmentation-3.0; без него наложение считается «не найдено» |

3. Откройте проект в Android Studio (AGP 8.5 / Kotlin 1.9, NDK 26 — при необходимости поправьте версии) и запустите на устройстве arm64.

## Что нужно сверить с вашими ONNX-экспортами (не проверялось без самих моделей)
- **GigaAM**: имена/порядок входов (`features`, `feature_lengths`), `LogMel(center=...)`, окно и нормировка, шаг кадра `frameMs_` (по умолчанию 40 мс), формат `vocab.txt` (символы или BPE с «▁»).
- **ECAPA**: код предполагает вход waveform `[1,N]`. Если модель ждёт fbank, добавьте расчёт признаков в `ml/ecapa.h`.
- **MossFormer**: вход waveform `[1,T]`, 2 источника; раскладка выхода определяется автоматически.
- **DTLN**: тензоры различаются по рангу (state — ранг 4). Если ваш экспорт другой, поправьте `DtlnSuppressor::bind`.
- **Silero**: имена `input/state/sr` → `output/stateN` (v5).
- Пороги: `SpeakerClusterer::kSame` (0.55), `Chunker` (доля речи 5%), `OverlapDetector` (0.3 с).

## Известные ограничения
- Весь проект не компилировался целиком (нет NDK, Oboe, ORT в среде генерации); отдельные заголовки без зависимостей проверены `g++ -fsyntax-only`.
- MossFormer + GigaAM могут не укладываться в 3 с на чанк (hop) на средних телефонах: смотрите счётчик `Chunker::dropped`, используйте int8, гейтинг по наложению, больший hop.
- Системный AEC зависит от устройства; запасной вариант — программный AEC3 с референсным сигналом.
