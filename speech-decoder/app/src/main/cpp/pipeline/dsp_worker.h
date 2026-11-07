#pragma once
// Этапы 5-9 в отдельном потоке (приоритет повышен): DTLN -> динамика -> Silero VAD -> чанкер.
#include <atomic>
#include <thread>
#include "core/spsc_ring.h"
#include "dsp/dynamics.h"
#include "ml/dtln.h"
#include "ml/silero_vad.h"
#include "pipeline/chunker.h"

class DspWorker {
public:
    DspWorker(SpscRing<float>& in, DtlnSuppressor& dtln, SileroVad& vad, Chunker& chunker)
        : in_(in), dtln_(dtln), vad_(vad), chunker_(chunker) { dyn_.init(16000.f); }
    ~DspWorker() { stop(); }

    void start();
    void stop();

private:
    void run();
    SpscRing<float>& in_;
    DtlnSuppressor& dtln_;
    SileroVad& vad_;
    Chunker& chunker_;
    Dynamics dyn_;
    std::thread th_;
    std::atomic<bool> running_{false};
};
