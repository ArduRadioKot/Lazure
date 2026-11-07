#include "pipeline/ml_orchestrator.h"
#include <algorithm>
#include <chrono>
#include <cmath>

void MlOrchestrator::start() {
    if (running_.exchange(true)) return;
    th_ = std::thread([this] { run(); });
}

void MlOrchestrator::stop() {
    if (!running_.exchange(false)) return;
    if (th_.joinable()) th_.join();
}

static float rmsOf(const float* x, int n) {
    double s = 0; for (int i = 0; i < n; ++i) s += (double)x[i] * x[i];
    return (float)std::sqrt(s / std::max(n, 1));
}

std::vector<MlOrchestrator::Utt> MlOrchestrator::groupByPause(const std::vector<Word>& w, int gapMs) {
    std::vector<Utt> out;
    for (const auto& x : w) {
        if (out.empty() || x.startMs - out.back().endMs > gapMs)
            out.push_back({x.startMs, x.endMs, x.text});
        else {
            out.back().text += " " + x.text;
            out.back().endMs = x.endMs;
        }
    }
    return out;
}

void MlOrchestrator::run() {
    int idx;
    while (running_.load(std::memory_order_relaxed)) {
        if (pool_.ready.read(&idx, 1) == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
            continue;
        }
        Chunk& c = pool_.chunks[idx];

        // Этап 10: разделение источников — только при наложенной речи
        std::vector<const float*> streams;
        if (moss_ && overlap_.hasOverlap(c.pcm, kChunk) && moss_->separate(c.pcm, kChunk, sep1_.data(), sep2_.data()))
            streams = {sep1_.data(), sep2_.data()};
        else
            streams = {c.pcm};

        std::vector<SegmentOut> segs;
        for (const float* s : streams) {
            if (rmsOf(s, kChunk) < 1e-3f) continue;                      // пустой канал сепаратора
            auto words = asr_.transcribe(s, kChunk);                     // Этап 11
            for (const auto& u : groupByPause(words, 600)) {
                const int64_t a = std::clamp<int64_t>(u.startMs * kSampleRate / 1000, 0, kChunk - 1);
                const int64_t b = std::clamp<int64_t>(u.endMs * kSampleRate / 1000, a + 1, kChunk);
                auto emb = ecapa_.embed(s + a, (int)(b - a));            // диаризация
                segs.push_back({u.startMs, u.endMs, clusterer_.assign(std::move(emb)), u.text});
            }
        }
        std::sort(segs.begin(), segs.end(), [](const SegmentOut& x, const SegmentOut& y) { return x.startMs < y.startMs; });
        merger_.addChunk(c.startSample, segs);                           // Этап 12
        pool_.freeq.write(&idx, 1);
    }
}
