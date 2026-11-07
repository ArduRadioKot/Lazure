
## Long files and first letter of a reply
- Words are never split between speakers: all pieces of a word go to the speaker who owns the word's middle (the cut from the diarizer/dash is a little off the real onset, and the first letter used to stay with the previous speaker).
- A 60-minute file ran out of memory (heap limit 512 MB) in `enhance`. Now: high-pass in place, DTLN mix and normalisation in place, noise suppression skipped above 30 min, VAD segments converted to floats on demand, decode buffer preallocated from the duration, diarization in 6-minute pieces. Emulator (2 GB RAM): 60 min processed in 365 s, no crash. Speaker quality on such a file is unmeasured (the file is a repeated 20-min clip).

## Length-independent processing
Audio never lives on the heap any more: the decoder writes PCM to a temp file (`Pcm`), high-pass, normalisation, DTLN (5-min pieces) and VAD stream through 60 s chunks, and only the span being recognised/embedded is read back. The 30-minute noise-suppression cut-off was removed. Emulator (2 GB RAM): 3 h processed in 1502 s, no OOM; short clips give identical transcripts. Diarization (6-min pieces) is about 60 % of the time on long files. Speaker quality on real multi-hour meetings is unmeasured (test file = repeated 20-min clip).
