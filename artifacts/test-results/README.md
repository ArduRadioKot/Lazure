# Артефакты тестирования

`results.csv` - вывод [`tools/eval/evaluate.py`](../../tools/eval/evaluate.py): по строке на запись и итог `TOTAL`.

```bash
python3 tools/eval/evaluate.py --ref refs/ --hyp exports/ --timing timing.csv --csv artifacts/test-results/results.csv
```

Колонки:

| Колонка | Смысл |
|---|---|
| `wer`, `wer_raw` | WER с нормализацией и без |
| `cpwer` | WER с учётом говорящих |
| `der`, `missed`, `false_alarm`, `confusion` | Диаризация и её составляющие (доли эталонной речи) |
| `ref_speakers`, `hyp_speakers` | Число говорящих в эталоне и в ответе |
| `start_mae_ms`, `end_mae_ms`, `within500`, `matched_segments` | Точность границ реплик |
| `audio_sec`, `processing_sec`, `rtf` | Скорость |

Рядом стоит сохранить:

- экспортированные JSON (`exports/`);
- `timing.csv`;
- модель устройства и тип сборки (release или debug).

Методика: [docs/TEST_RESULTS.md](../../docs/TEST_RESULTS.md).
