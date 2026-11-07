#pragma once
// Этап 12: склейка перекрывающихся чанков правилом «центрального среза»:
// из чанка берутся сегменты, середина которых лежит в [start+0.5с, end-0.5с); первый чанк — с нуля.
#include <functional>
#include <string>
#include <vector>
#include <cstdint>
#include "core/chunk_pool.h"

struct SegmentOut {
    int64_t startMs, endMs;
    int speaker;
    std::string text;
};

class TranscriptMerger {
public:
    using Cb = std::function<void(const SegmentOut&)>;
    explicit TranscriptMerger(Cb cb) : cb_(std::move(cb)) {}

    // seg.startMs/endMs — относительно начала чанка
    void addChunk(int64_t chunkStartSample, std::vector<SegmentOut>& segs) {
        const bool first = (chunkStartSample == 0);
        const int64_t t0 = chunkStartSample * 1000 / kSampleRate;
        const int64_t lo = first ? INT64_MIN : t0 + (int64_t)(kOverlap / 2) * 1000 / kSampleRate;
        const int64_t hi = t0 + (int64_t)(kChunk - kOverlap / 2) * 1000 / kSampleRate;
        for (auto& s : segs) {
            s.startMs += t0; s.endMs += t0;
            const int64_t mid = (s.startMs + s.endMs) / 2;
            if (mid >= lo && mid < hi) cb_(s);
        }
    }

private:
    Cb cb_;
};
