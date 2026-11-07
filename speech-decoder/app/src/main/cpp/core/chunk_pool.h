#pragma once
// Предвыделенный пул 4-секундных чанков. Между потоками ходят только индексы слотов.
#include <array>
#include <cstdint>
#include "core/spsc_ring.h"

constexpr int kSampleRate = 16000;
constexpr int kChunk      = 64000;                 // 4 с
constexpr int kOverlap    = 16000;                 // 1 с перекрытия
constexpr int kHopChunk   = kChunk - kOverlap;     // шаг 3 с
constexpr int kVadFrame   = 512;

struct Chunk {
    float   pcm[kChunk];
    int64_t startSample;
    float   speechRatio;
};

struct ChunkPool {
    static constexpr int N = 6;
    std::array<Chunk, N> chunks;
    SpscRing<int> ready{8};    // DspWorker -> MlOrchestrator
    SpscRing<int> freeq{8};    // MlOrchestrator -> DspWorker
    ChunkPool() { for (int i = 0; i < N; ++i) freeq.write(&i, 1); }
};
