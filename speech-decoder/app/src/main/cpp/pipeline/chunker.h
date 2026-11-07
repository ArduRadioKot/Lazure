#pragma once
// Этап 9: формирование 4-секундных чанков (64000) с перекрытием 1 с. Чанки с долей речи < 5% отбрасываются.
#include <atomic>
#include <vector>
#include "core/chunk_pool.h"

class Chunker {
public:
    explicit Chunker(ChunkPool& p) : pool_(p), tl_(kTl), flags_(kFlagN, 0) {}

    void onVadFrame(bool speech) { flags_[vadFrames_++ & (kFlagN - 1)] = speech ? 1 : 0; }

    void push(const float* x, size_t n) {
        for (size_t i = 0; i < n; ++i) tl_[(abs_ + i) & (kTl - 1)] = x[i];
        abs_ += (int64_t)n;
        while (abs_ >= nextEnd_) { emit(nextEnd_ - kChunk); nextEnd_ += kHopChunk; }
    }

    std::atomic<uint32_t> dropped{0}, skipped{0};

private:
    static constexpr int64_t kTl = 1 << 17, kFlagN = 1 << 10;

    void emit(int64_t start) {
        const int64_t f0 = start / kVadFrame, f1 = (start + kChunk) / kVadFrame;
        int sp = 0;
        for (int64_t f = f0; f < f1; ++f) sp += flags_[f & (kFlagN - 1)];
        const float ratio = float(sp) / float(f1 - f0);
        if (ratio < 0.05f) { skipped++; return; }               // тишина — ML не трогаем
        int idx;
        if (pool_.freeq.read(&idx, 1) == 0) { dropped++; return; }   // ML не успевает
        Chunk& c = pool_.chunks[idx];
        for (int i = 0; i < kChunk; ++i) c.pcm[i] = tl_[(start + i) & (kTl - 1)];
        c.startSample = start;
        c.speechRatio = ratio;
        pool_.ready.write(&idx, 1);
    }

    ChunkPool& pool_;
    std::vector<float> tl_;
    std::vector<uint8_t> flags_;
    int64_t abs_ = 0, nextEnd_ = kChunk, vadFrames_ = 0;
};
