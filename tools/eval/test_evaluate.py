"""Self-tests for evaluate.py: python3 -m unittest discover -s tools/eval"""

import json
import tempfile
import unittest
from pathlib import Path

import evaluate as ev

S = ev.Segment


class TextMetrics(unittest.TestCase):
    def test_normalize(self):
        self.assertEqual(ev.normalize("Ёлка, ЁЖ! Добрый-день..."), ["елка", "еж", "добрый", "день"])

    def test_edit_distance(self):
        self.assertEqual(ev.edit_distance("a b c".split(), "a x c d".split()), 2)
        self.assertEqual(ev.edit_distance([], ["a"]), 1)
        self.assertEqual(ev.edit_distance(["a"], []), 1)

    def test_wer_ignores_case_and_punctuation(self):
        ref = [S("speaker_1", 0, 1000, "Добрый день!")]
        hyp = [S("speaker_1", 0, 1000, "добрый день")]
        self.assertEqual(ev.wer(ref, hyp), (0, 2))
        self.assertEqual(ev.wer(ref, hyp, ev.raw_words), (2, 2))

    def test_cpwer_penalises_wrong_speaker(self):
        ref = [S("a", 0, 1000, "привет мир"), S("b", 1000, 2000, "как дела")]
        same = [S("x", 0, 1000, "привет мир"), S("y", 1000, 2000, "как дела")]
        swapped = [S("x", 0, 1000, "привет мир как"), S("y", 1000, 2000, "дела")]
        self.assertEqual(ev.cp_wer(ref, same), (0, 4))
        self.assertEqual(ev.cp_wer(ref, swapped)[0], 2)
        self.assertEqual(ev.wer(ref, swapped)[0], 0)     # plain WER does not see speakers


class Assignment(unittest.TestCase):
    def test_hungarian(self):
        cost = [[4, 1, 3], [2, 0, 5], [3, 2, 2]]
        a = ev.hungarian_min(cost)
        self.assertEqual(sum(cost[i][a[i]] for i in range(3)), 5)
        self.assertEqual(sorted(a), [0, 1, 2])


class Diarization(unittest.TestCase):
    def test_perfect_with_renamed_speakers(self):
        ref = [S("a", 0, 2000, ""), S("b", 2000, 4000, "")]
        hyp = [S("speaker_2", 0, 2000, ""), S("speaker_1", 2000, 4000, "")]
        self.assertAlmostEqual(ev.der(ref, hyp, collar_s=0).der, 0.0)

    def test_confusion_missed_false_alarm(self):
        ref = [S("a", 0, 2000, ""), S("b", 2000, 4000, "")]
        one_speaker = [S("x", 0, 4000, "")]
        d = ev.der(ref, one_speaker, collar_s=0)
        self.assertAlmostEqual(d.der, 0.5)
        self.assertAlmostEqual(d.confusion, 2.0)
        missed = ev.der(ref, [S("x", 0, 2000, "")], collar_s=0)
        self.assertAlmostEqual(missed.missed, 2.0)
        fa = ev.der(ref, ref + [S("z", 4000, 5000, "")], collar_s=0)
        self.assertAlmostEqual(fa.false_alarm, 1.0)

    def test_collar_forgives_small_shift(self):
        ref = [S("a", 0, 2000, ""), S("b", 2000, 4000, "")]
        hyp = [S("a", 0, 2100, ""), S("b", 2100, 4000, "")]
        self.assertGreater(ev.der(ref, hyp, collar_s=0).der, 0)
        self.assertAlmostEqual(ev.der(ref, hyp, collar_s=0.25).der, 0.0)


class Boundaries(unittest.TestCase):
    def test_boundary_errors(self):
        ref = [S("a", 1000, 3000, ""), S("b", 4000, 6000, "")]
        hyp = [S("a", 1100, 2900, ""), S("b", 4000, 5000, "")]
        b = ev.boundaries(ref, hyp)
        self.assertEqual(b.matched, 2)
        self.assertAlmostEqual(b.start_mae_ms, 50)
        self.assertAlmostEqual(b.end_mae_ms, 550)
        self.assertAlmostEqual(b.within_500, 0.5)


class EndToEnd(unittest.TestCase):
    def test_directories_and_timing(self):
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            (d / "ref").mkdir(); (d / "hyp").mkdir()
            doc = {"transcript": [{"speaker_id": "speaker_1", "start_ms": 0, "end_ms": 1000, "text": "Добрый день"}]}
            (d / "ref" / "cat1.json").write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
            (d / "hyp" / "cat1.json").write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
            (d / "t.csv").write_text("file,audio_sec,processing_sec\ncat1.wav,10,2\n", encoding="utf-8")
            rows, total = ev.evaluate(ev.pair_files(d / "ref", d / "hyp"), ev.load_timing(d / "t.csv"), 0.25)
            self.assertEqual(rows[0]["wer"], 0.0)
            self.assertAlmostEqual(total["rtf"], 0.2)
            self.assertEqual(ev.main(["--ref", str(d / "ref"), "--hyp", str(d / "hyp"), "--csv", str(d / "o.csv")]), 0)
            self.assertTrue((d / "o.csv").exists())


if __name__ == "__main__":
    unittest.main()
