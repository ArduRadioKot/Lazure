# Speaker-labelling benchmark (synthetic, known ground truth)

`gen_dialogs.py` (macOS `say` + ffmpeg) writes four Russian dialogues with a JSON of true turns: `rapid3` (3 voices, 0.05-0.3 s gaps,
one-word replies), `dialog2`, `long2` (long monologue turns), `mixed3`. The "voices" are Milena pitch-shifted by 0.8x / 1.22x, so
they are MORE alike than two real people; use the numbers to compare versions, not as an absolute accuracy.

Run: push the wavs to the app's `files/eval/`, run `SpeechPipelineDeviceTest#transcribesPushedFiles`, then `python3 evaluate_speakers.py [name]`.
It reads `SPEECH_EVAL` from `adb logcat` and prints speech-time accuracy (best speaker mapping), coverage and label accuracy on covered
speech; with a name it lists every turn that was labelled wrongly.
