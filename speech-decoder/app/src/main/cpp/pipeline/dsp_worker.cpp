#include "pipeline/dsp_worker.h"
#include <sys/resource.h>
#include <unistd.h>
#include <chrono>

void DspWorker::start() {
    if (running_.exchange(true)) return;
    th_ = std::thread([this] { run(); });
}

void DspWorker::stop() {
    if (!running_.exchange(false)) return;
    if (th_.joinable()) th_.join();
}

void DspWorker::run() {
    setpriority(PRIO_PROCESS, gettid(), -16);          // best-effort
    constexpr int H = DtlnSuppressor::kShift;
    float hop[H], den[H];
    while (running_.load(std::memory_order_relaxed)) {
        if (in_.available() < (size_t)H) {
            std::this_thread::sleep_for(std::chrono::milliseconds(3));   // hop = 8 мс
            continue;
        }
        in_.read(hop, H);
        dtln_.processHop(hop, den);                                       // Этап 5
        dyn_.process(den, H);                                             // Этапы 6-7
        vad_.feed(den, H, [&](bool sp) { chunker_.onVadFrame(sp); });     // Этап 8
        chunker_.push(den, H);                                            // Этап 9
    }
}
