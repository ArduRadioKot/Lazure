#!/usr/bin/env python3
"""Scores Lazure transcripts against reference transcripts.

Both sides use the competition JSON format:

    {"transcript": [{"speaker_id": "speaker_1", "start_ms": 1250, "end_ms": 4380, "text": "Добрый день!"}]}

Usage:
    python3 tools/eval/evaluate.py --ref refs/ --hyp exports/ [--timing timing.csv] [--csv out.csv]

`--ref` and `--hyp` are files or directories; inside directories files are paired by name (stem). `timing.csv`
has the columns `file,audio_sec,processing_sec` (the app shows processing time and RTF on the recording card).

Metrics per file and in total:
  * WER    - word error rate of the whole conversation, words in start-time order, after normalisation
             (lower case, ё -> е, punctuation removed). WER_raw is the same without normalisation.
  * cpWER  - concatenated minimum-permutation WER: words grouped per speaker, best speaker mapping. Penalises
             both recognition errors and words given to the wrong speaker.
  * DER    - diarization error rate (missed speech + false alarm + speaker confusion) / reference speech,
             10 ms frames, best one-to-one speaker mapping, `--collar` seconds ignored around reference boundaries.
  * spk    - number of speakers in the reference / hypothesis.
  * start/end MAE - mean absolute boundary error of reference segments matched to the hypothesis segment with
             the largest time overlap (IoU >= 0.5); `within500` is the share of matched segments whose both
             boundaries are within 500 ms.
  * RTF    - processing time / audio duration (only with --timing).

Only the Python standard library is used.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Segment:
    speaker: str
    start_ms: int
    end_ms: int
    text: str


def load_transcript(path: Path) -> list[Segment]:
    data = json.loads(path.read_text(encoding="utf-8"))
    items = data["transcript"] if isinstance(data, dict) else data
    out = []
    for i, it in enumerate(items):
        try:
            out.append(Segment(str(it["speaker_id"]), int(it["start_ms"]), int(it["end_ms"]), str(it.get("text", ""))))
        except (KeyError, TypeError, ValueError) as e:
            raise ValueError(f"{path}: segment {i} is malformed: {e}") from e
    return sorted(out, key=lambda s: (s.start_ms, s.end_ms))


# ---------------------------------------------------------------- text

_PUNCT = re.compile(r"[^\w\s]|_", re.UNICODE)


def normalize(text: str) -> list[str]:
    t = unicodedata.normalize("NFC", text).lower().replace("ё", "е")
    t = _PUNCT.sub(" ", t)
    return t.split()


def raw_words(text: str) -> list[str]:
    return text.split()


def edit_distance(ref: list[str], hyp: list[str]) -> int:
    """Levenshtein distance over words (substitutions + deletions + insertions)."""
    if not ref:
        return len(hyp)
    if not hyp:
        return len(ref)
    prev = list(range(len(hyp) + 1))
    for i, r in enumerate(ref, 1):
        cur = [i] + [0] * len(hyp)
        for j, h in enumerate(hyp, 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (r != h))
        prev = cur
    return prev[-1]


# ---------------------------------------------------------------- assignment

def hungarian_min(cost: list[list[float]]) -> list[int]:
    """Minimum-cost assignment for a square matrix; returns column index for each row (Kuhn-Munkres, O(n^3))."""
    n = len(cost)
    if n == 0:
        return []
    inf = float("inf")
    u = [0.0] * (n + 1)
    v = [0.0] * (n + 1)
    p = [0] * (n + 1)
    way = [0] * (n + 1)
    for i in range(1, n + 1):
        p[0] = i
        j0 = 0
        minv = [inf] * (n + 1)
        used = [False] * (n + 1)
        while True:
            used[j0] = True
            i0 = p[j0]
            delta = inf
            j1 = 0
            for j in range(1, n + 1):
                if not used[j]:
                    c = cost[i0 - 1][j - 1] - u[i0] - v[j]
                    if c < minv[j]:
                        minv[j] = c
                        way[j] = j0
                    if minv[j] < delta:
                        delta = minv[j]
                        j1 = j
            for j in range(n + 1):
                if used[j]:
                    u[p[j]] += delta
                    v[j] -= delta
                else:
                    minv[j] -= delta
            j0 = j1
            if p[j0] == 0:
                break
        while True:
            j1 = way[j0]
            p[j0] = p[j1]
            j0 = j1
            if j0 == 0:
                break
    result = [0] * n
    for j in range(1, n + 1):
        if p[j] > 0:
            result[p[j] - 1] = j - 1
    return result


def speakers(segs: list[Segment]) -> list[str]:
    seen: dict[str, None] = {}
    for s in segs:
        seen.setdefault(s.speaker, None)
    return list(seen)


# ---------------------------------------------------------------- metrics

def wer(ref: list[Segment], hyp: list[Segment], norm=normalize) -> tuple[int, int]:
    r = [w for s in ref for w in norm(s.text)]
    h = [w for s in hyp for w in norm(s.text)]
    return edit_distance(r, h), len(r)


def cp_wer(ref: list[Segment], hyp: list[Segment]) -> tuple[int, int]:
    rs, hs = speakers(ref), speakers(hyp)
    rw = {k: [w for s in ref if s.speaker == k for w in normalize(s.text)] for k in rs}
    hw = {k: [w for s in hyp if s.speaker == k for w in normalize(s.text)] for k in hs}
    n = max(len(rs), len(hs))
    if n == 0:
        return 0, 0
    cost = [[0.0] * n for _ in range(n)]
    for i in range(n):
        for j in range(n):
            if i < len(rs) and j < len(hs):
                cost[i][j] = edit_distance(rw[rs[i]], hw[hs[j]])
            elif i < len(rs):
                cost[i][j] = len(rw[rs[i]])        # reference speaker without a match: all words deleted
            elif j < len(hs):
                cost[i][j] = len(hw[hs[j]])        # extra hypothesis speaker: all words inserted
    assign = hungarian_min(cost)
    errors = int(sum(cost[i][assign[i]] for i in range(n)))
    return errors, sum(len(v) for v in rw.values())


@dataclass
class DerResult:
    missed: float
    false_alarm: float
    confusion: float
    total: float

    @property
    def der(self) -> float:
        return (self.missed + self.false_alarm + self.confusion) / self.total if self.total else 0.0


def der(ref: list[Segment], hyp: list[Segment], collar_s: float = 0.25, frame_ms: int = 10) -> DerResult:
    end = max([s.end_ms for s in ref + hyp], default=0)
    n = end // frame_ms + 1
    rs, hs = speakers(ref), speakers(hyp)
    ri = {k: i for i, k in enumerate(rs)}
    hi = {k: i for i, k in enumerate(hs)}
    rf = [set() for _ in range(n)]
    hf = [set() for _ in range(n)]
    for s in ref:
        for f in range(s.start_ms // frame_ms, s.end_ms // frame_ms):
            rf[f].add(ri[s.speaker])
    for s in hyp:
        for f in range(s.start_ms // frame_ms, s.end_ms // frame_ms):
            hf[f].add(hi[s.speaker])
    scored = [True] * n
    if collar_s > 0:
        c = int(collar_s * 1000 / frame_ms)
        for s in ref:
            for b in (s.start_ms // frame_ms, s.end_ms // frame_ms):
                for f in range(max(0, b - c), min(n, b + c)):
                    scored[f] = False
    overlap = [[0] * len(hs) for _ in rs]
    for f in range(n):
        if scored[f]:
            for a in rf[f]:
                for b in hf[f]:
                    overlap[a][b] += 1
    size = max(len(rs), len(hs))
    cost = [[-(overlap[i][j] if i < len(rs) and j < len(hs) else 0) for j in range(size)] for i in range(size)]
    assign = hungarian_min(cost) if size else []
    mapping = {i: assign[i] for i in range(len(rs)) if assign[i] < len(hs)}
    missed = fa = conf = total = 0
    for f in range(n):
        if not scored[f]:
            continue
        r, h = rf[f], hf[f]
        total += len(r)
        missed += max(0, len(r) - len(h))
        fa += max(0, len(h) - len(r))
        correct = sum(1 for a in r if mapping.get(a) in h)
        conf += min(len(r), len(h)) - correct
    k = frame_ms / 1000
    return DerResult(missed * k, fa * k, conf * k, total * k)


@dataclass
class BoundaryResult:
    matched: int
    total: int
    start_mae_ms: float
    end_mae_ms: float
    within_500: float


def boundaries(ref: list[Segment], hyp: list[Segment], min_iou: float = 0.5) -> BoundaryResult:
    ds, de, ok = [], [], 0
    for r in ref:
        best, best_iou = None, 0.0
        for h in hyp:
            inter = min(r.end_ms, h.end_ms) - max(r.start_ms, h.start_ms)
            if inter <= 0:
                continue
            iou = inter / (max(r.end_ms, h.end_ms) - min(r.start_ms, h.start_ms))
            if iou > best_iou:
                best, best_iou = h, iou
        if best is None or best_iou < min_iou:
            continue
        a, b = abs(best.start_ms - r.start_ms), abs(best.end_ms - r.end_ms)
        ds.append(a)
        de.append(b)
        ok += a <= 500 and b <= 500
    m = len(ds)
    return BoundaryResult(m, len(ref), sum(ds) / m if m else float("nan"), sum(de) / m if m else float("nan"),
                          ok / m if m else float("nan"))


# ---------------------------------------------------------------- driver

def pair_files(ref: Path, hyp: Path) -> list[tuple[str, Path, Path]]:
    if ref.is_file() and hyp.is_file():
        return [(ref.stem, ref, hyp)]
    if ref.is_dir() and hyp.is_dir():
        hyps = {p.stem: p for p in hyp.glob("*.json")}
        pairs = [(p.stem, p, hyps[p.stem]) for p in sorted(ref.glob("*.json")) if p.stem in hyps]
        missing = [p.stem for p in sorted(ref.glob("*.json")) if p.stem not in hyps]
        if missing:
            print(f"warning: no hypothesis for {', '.join(missing)}", file=sys.stderr)
        return pairs
    raise SystemExit("--ref and --hyp must both be files or both be directories")


def load_timing(path: Path | None) -> dict[str, tuple[float, float]]:
    if path is None:
        return {}
    out = {}
    with path.open(encoding="utf-8") as f:
        for row in csv.DictReader(f):
            out[Path(row["file"]).stem] = (float(row["audio_sec"]), float(row["processing_sec"]))
    return out


def evaluate(pairs, timing, collar):
    rows = []
    tot = dict(we=0, wn=0, re=0, rn=0, ce=0, cn=0, miss=0.0, fa=0.0, conf=0.0, speech=0.0, audio=0.0, proc=0.0)
    for name, rp, hp in pairs:
        ref, hyp = load_transcript(rp), load_transcript(hp)
        we, wn = wer(ref, hyp)
        re_, rn = wer(ref, hyp, raw_words)
        ce, cn = cp_wer(ref, hyp)
        d = der(ref, hyp, collar)
        b = boundaries(ref, hyp)
        audio, proc = timing.get(name, (float("nan"), float("nan")))
        rows.append(dict(
            file=name, wer=we / wn if wn else 0.0, wer_raw=re_ / rn if rn else 0.0, cpwer=ce / cn if cn else 0.0,
            der=d.der, missed=d.missed / d.total if d.total else 0.0, false_alarm=d.false_alarm / d.total if d.total else 0.0,
            confusion=d.confusion / d.total if d.total else 0.0, ref_speakers=len(speakers(ref)),
            hyp_speakers=len(speakers(hyp)), start_mae_ms=b.start_mae_ms, end_mae_ms=b.end_mae_ms,
            within500=b.within_500, matched_segments=f"{b.matched}/{b.total}", audio_sec=audio,
            processing_sec=proc, rtf=proc / audio if audio == audio and audio > 0 else float("nan"),
        ))
        tot["we"] += we; tot["wn"] += wn; tot["re"] += re_; tot["rn"] += rn; tot["ce"] += ce; tot["cn"] += cn
        tot["miss"] += d.missed; tot["fa"] += d.false_alarm; tot["conf"] += d.confusion; tot["speech"] += d.total
        if audio == audio and proc == proc:
            tot["audio"] += audio; tot["proc"] += proc
    s = tot["speech"]
    total = dict(
        file="TOTAL", wer=tot["we"] / tot["wn"] if tot["wn"] else 0.0, wer_raw=tot["re"] / tot["rn"] if tot["rn"] else 0.0,
        cpwer=tot["ce"] / tot["cn"] if tot["cn"] else 0.0, der=(tot["miss"] + tot["fa"] + tot["conf"]) / s if s else 0.0,
        missed=tot["miss"] / s if s else 0.0, false_alarm=tot["fa"] / s if s else 0.0, confusion=tot["conf"] / s if s else 0.0,
        ref_speakers="", hyp_speakers="", start_mae_ms=_mean(rows, "start_mae_ms"), end_mae_ms=_mean(rows, "end_mae_ms"),
        within500=_mean(rows, "within500"), matched_segments="", audio_sec=tot["audio"] or float("nan"),
        processing_sec=tot["proc"] or float("nan"), rtf=tot["proc"] / tot["audio"] if tot["audio"] else float("nan"),
    )
    return rows, total


def _mean(rows, key):
    vals = [r[key] for r in rows if r[key] == r[key]]
    return sum(vals) / len(vals) if vals else float("nan")


def _fmt(v, pct=False):
    if isinstance(v, (str, int)):
        return str(v)
    if v != v:
        return "-"
    return f"{100 * v:.1f}%" if pct else (f"{v:.0f}" if abs(v) >= 10 else f"{v:.2f}")


COLUMNS = [("file", False), ("wer", True), ("wer_raw", True), ("cpwer", True), ("der", True), ("missed", True),
           ("false_alarm", True), ("confusion", True), ("ref_speakers", False), ("hyp_speakers", False),
           ("start_mae_ms", False), ("end_mae_ms", False), ("within500", True), ("rtf", False)]


def print_table(rows, total):
    table = [[c for c, _ in COLUMNS]] + [[_fmt(r[c], p) for c, p in COLUMNS] for r in rows + [total]]
    widths = [max(len(str(row[i])) for row in table) for i in range(len(COLUMNS))]
    for k, row in enumerate(table):
        print("  ".join(str(x).rjust(w) for x, w in zip(row, widths)))
        if k == 0:
            print("  ".join("-" * w for w in widths))


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--ref", type=Path, required=True, help="reference JSON file or directory")
    ap.add_argument("--hyp", type=Path, required=True, help="exported JSON file or directory")
    ap.add_argument("--timing", type=Path, help="CSV file,audio_sec,processing_sec")
    ap.add_argument("--collar", type=float, default=0.25, help="DER collar around reference boundaries, s")
    ap.add_argument("--csv", type=Path, help="write per-file metrics to this CSV")
    args = ap.parse_args(argv)
    pairs = pair_files(args.ref, args.hyp)
    if not pairs:
        print("no file pairs found", file=sys.stderr)
        return 1
    rows, total = evaluate(pairs, load_timing(args.timing), args.collar)
    print_table(rows, total)
    if args.csv:
        with args.csv.open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0]))
            w.writeheader()
            w.writerows(rows + [total])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
