#pragma once
// Этапы 0-4 в Oboe-callback: capture, (AEC в HAL), downmix, resample 16k, HPF.
#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include <vector>
#include "core/spsc_ring.h"
#include "dsp/resampler.h"
#include "dsp/biquad.h"

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback,
                    public std::enable_shared_from_this<AudioEngine> {
public:
    explicit AudioEngine(SpscRing<float>& out16k) : ring_(out16k) {}
    bool start();
    void stop();
    int  sessionId() const { return sessionId_; }
    int  sampleRate() const { return sr_; }
    uint32_t overruns() const { return overruns_.load(); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override;
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result) override;

private:
    static constexpr int kSlice = 2048;
    SpscRing<float>& ring_;
    std::shared_ptr<oboe::AudioStream> stream_;
    Resampler resampler_;
    BiquadHpf hpf_;
    std::vector<float> mono_, out_;                 // предвыделено в start()
    int sr_ = 0, ch_ = 1, sessionId_ = -1;
    std::atomic<uint32_t> overruns_{0};
    std::atomic<bool> running_{false};
};
