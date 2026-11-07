#!/usr/bin/env python3
"""Measures how well reply boundaries follow from GigaAM v3 CTC token timestamps.

Builds a Russian dialogue with known speech boundaries (espeak-ng phrases in two voices plus the real example.wav
shipped with the model, random pauses 0.15-2 s, faint noise), runs Silero VAD with the app's parameters and padding
and GigaAM per VAD segment (sherpa-onnx, the same version as the app). Speakers are taken from ground truth, so only
timing is measured. For every reply it compares:

  * start            - first token timestamp (what the app uses);
  * start+X          - last token timestamp + X ms as the reply end (the app uses X = 80);
  * next/VAD end     - end at the next token of the VAD segment or at the VAD speech end.

Result on 2026-11-07, three dialogues, 24 correctly recognised replies: last token + 80 ms had MAE 0.38 s; the
next/VAD-end rule was 0.3-0.7 s late on average (MAE 0.54 s), because CTC emits a token near its end and the VAD
segment end carries ~0.4 s of trailing silence. The app therefore keeps "last token + 80 ms".

Setup (outside the app):
    pip install sherpa-onnx==2.0.1 numpy soundfile ; apt-get install espeak-ng
    curl -L https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx -o models/silero_vad.onnx
    curl -L https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16.tar.bz2 | tar xj -C models
Run:
    python3 tools/bench/token_timing_check.py models
"""
import random
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
import sherpa_onnx
import soundfile as sf

M = Path(sys.argv[1])
ASR = M / "sherpa-onnx-nemo-ctc-punct-giga-am-v3-russian-2025-12-16"
SR = 16000
OFFSETS = (0, 80, 150, 250, 400)

PHRASES = [
    "Ты опять съел мою еду?", "Нет.", "Тогда кто оставил крошки у миски?", "Наверное, это был пёс.",
    "У нас нет пса.", "Значит, это был очень хитрый пёс.", "Хорошо, давай проверим камеру.", "Угу.",
    "Камера показывает, что это был ты.", "Камера врёт.", "Завтра собрание в десять утра.",
    "Я приду, но немного опоздаю.", "Принеси, пожалуйста, отчёт за прошлую неделю.", "Да, конечно.",
    "Почему мышей стало больше?", "Потому что мы спали весь день.",
]


def tts(text, voice, path):
    subprocess.run(["espeak-ng", "-v", voice, "-s", "150", "-w", str(path), text], check=True)
    x, sr = sf.read(path, dtype="float32")
    if sr != SR:
        idx = np.arange(0, len(x) * SR / sr) * sr / SR
        x = np.interp(idx, np.arange(len(x)), x).astype("float32")
    return x


def speech_bounds(x):
    """Speech start/end of a clean clip by 10 ms frame energy, relative to its peak."""
    f = 160
    n = len(x) // f
    db = 20 * np.log10(np.sqrt((x[: n * f].reshape(n, f) ** 2).mean(1)) + 1e-9)
    on = np.where(db > max(db.max() - 35, -55))[0]
    return on[0] * f, (on[-1] + 1) * f


def build(seed):
    rnd = random.Random(seed)
    tmp = Path(tempfile.mkdtemp())
    clips = [tts(p, "ru" if i % 2 == 0 else "ru+f3", tmp / f"{i}.wav") for i, p in enumerate(PHRASES)]
    real, sr = sf.read(ASR / "test_wavs" / "example.wav", dtype="float32")
    assert sr == SR
    clips.insert(5, real)
    audio = [np.zeros(int(0.5 * SR), "float32")]
    pos = len(audio[0])
    gt = []
    for x in clips:
        s, e = speech_bounds(x)
        gt.append(((pos + s) / SR, (pos + e) / SR))
        gap = int(rnd.choice([0.15, 0.3, 0.6, 1.2, 2.0]) * SR)
        audio += [x, np.zeros(gap, "float32")]
        pos += len(x) + gap
    y = np.concatenate(audio)
    y += np.random.default_rng(seed).normal(0, 0.002, len(y)).astype("float32")
    return y, gt


def vad_segments(y):
    """Silero VAD with SpeechPipeline.detectSpeech parameters and padding: (start sample, audio, speech end ms)."""
    cfg = sherpa_onnx.VadModelConfig()
    cfg.silero_vad.model = str(M / "silero_vad.onnx")
    cfg.silero_vad.threshold = 0.45
    cfg.silero_vad.min_silence_duration = 0.4
    cfg.silero_vad.min_speech_duration = 0.25
    cfg.silero_vad.window_size = 512
    cfg.silero_vad.max_speech_duration = 25
    cfg.sample_rate = SR
    vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=600)
    raw = []

    def drain():
        while not vad.empty():
            raw.append((vad.front.start, len(vad.front.samples)))
            vad.pop()

    for i in range(0, len(y), 512):
        vad.accept_waveform(y[i:i + 512])
        drain()
    vad.flush()
    drain()
    out = []
    for k, (st0, n) in enumerate(raw):
        prev_end = raw[k - 1][0] + raw[k - 1][1] if k else 0
        next_start = raw[k + 1][0] if k + 1 < len(raw) else len(y)
        st = max(0, st0 - min(4000, max(0, (st0 - prev_end) // 2)))
        en = min(len(y), st0 + n + min(2400, max(0, (next_start - (st0 + n)) // 2)))
        out.append((st, y[st:en], (st0 + n) * 1000 // SR))
    return out


def run(seed, rec, results):
    y, gt = build(seed)
    toks = []   # (start_ms, next_or_vad_end_ms, text)
    for st, x, speech_end in vad_segments(y):
        s = rec.create_stream()
        s.accept_waveform(SR, x)
        rec.decode_stream(s)
        r = s.result
        starts = [st * 1000 // SR + int(t * 1000) for t in r.timestamps]
        for k, t in enumerate(r.tokens):
            nxt = starts[k + 1] if k + 1 < len(starts) else speech_end
            toks.append((starts[k], min(max(nxt, starts[k] + 80), starts[k] + 1500), t))
    for i, (a, b) in enumerate(gt):
        lo = (gt[i - 1][1] + a) / 2 if i else -1
        hi = (b + gt[i + 1][0]) / 2 if i + 1 < len(gt) else 1e9
        mine = [t for t in toks if lo * 1000 <= t[0] < hi * 1000]
        text = "".join(t[2] for t in mine).replace("▁", " ").strip()
        if not mine or abs(mine[0][0] / 1000 - a) >= 0.5 or len(text) <= 2:
            continue   # misrecognised or mis-assigned reply: its timing says nothing about the rule
        results["start"].append(mine[0][0] / 1000 - a)
        for x in OFFSETS:
            results[f"last+{x}ms"].append(mine[-1][0] / 1000 + x / 1000 - b)
        results["next/VAD end"].append(mine[-1][1] / 1000 - b)


def main():
    rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=str(ASR / "model.int8.onnx"), tokens=str(ASR / "tokens.txt"), num_threads=4)
    results = {"start": [], **{f"last+{x}ms": [] for x in OFFSETS}, "next/VAD end": []}
    for seed in (1, 2, 3):
        run(seed, rec, results)
    for k, v in results.items():
        v = np.array(v)
        print(f"{k:13s} bias {v.mean():+.3f} s  MAE {np.abs(v).mean():.3f} s  "
              f"within 0.5 s {np.mean(np.abs(v) <= 0.5) * 100:3.0f}%  n={len(v)}")


if __name__ == "__main__":
    main()
