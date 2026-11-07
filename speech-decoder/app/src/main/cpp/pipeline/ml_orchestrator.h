#pragma once
// Этапы 10-12 в фоновом потоке: [overlap?] MossFormer -> GigaAM -> ECAPA -> кластеризация -> merge.
#include <atomic>
#include <thread>
#include <vector>
#include "core/chunk_pool.h"
#include "ml/gigaam.h"
#include "ml/mossformer.h"
#include "ml/ecapa.h"
#include "ml/overlap_detector.h"
#include "pipeline/clusterer.h"
#include "pipeline/merger.h"

class MlOrchestrator {
public:
    MlOrchestrator(ChunkPool& pool, OverlapDetector& overlap, MossFormer* moss,
                   GigaAm& asr, Ecapa& ecapa, TranscriptMerger& merger)
        : pool_(pool), overlap_(overlap), moss_(moss), asr_(asr), ecapa_(ecapa), merger_(merger),
          sep1_(kChunk), sep2_(kChunk) {}
    ~MlOrchestrator() { stop(); }

    void start();
    void stop();

private:
    struct Utt { int64_t startMs, endMs; std::string text; };
    static std::vector<Utt> groupByPause(const std::vector<Word>& w, int gapMs);
    void run();

    ChunkPool& pool_;
    OverlapDetector& overlap_;
    MossFormer* moss_;                  // может быть nullptr
    GigaAm& asr_;
    Ecapa& ecapa_;
    TranscriptMerger& merger_;
    SpeakerClusterer clusterer_;
    std::vector<float> sep1_, sep2_;
    std::thread th_;
    std::atomic<bool> running_{false};
};
