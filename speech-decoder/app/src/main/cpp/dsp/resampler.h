#pragma once
// Этап 3: ресемплинг в 16 kHz, libsamplerate SRC_SINC_MEDIUM_QUALITY (anti-aliasing внутри sinc-фильтра).
#include <samplerate.h>
#include <cmath>
#include <cstring>
#include <cstddef>

class Resampler {
public:
    ~Resampler() { if (st_) src_delete(st_); }

    bool init(int inRate) {                 // вне audio-thread
        if (st_) { src_delete(st_); st_ = nullptr; }
        bypass_ = (inRate == 16000);
        ratio_  = 16000.0 / inRate;
        if (bypass_) return true;
        int err = 0;
        st_ = src_new(SRC_SINC_MEDIUM_QUALITY, 1, &err);
        return st_ != nullptr;
    }

    size_t outCapacity(size_t inFrames) const { return (size_t)std::ceil(inFrames * ratio_) + 64; }

    size_t process(const float* in, size_t n, float* out, size_t outCap) {
        if (bypass_) { std::memcpy(out, in, n * sizeof(float)); return n; }
        SRC_DATA d{};
        d.data_in = in;  d.input_frames = (long)n;
        d.data_out = out; d.output_frames = (long)outCap;
        d.src_ratio = ratio_; d.end_of_input = 0;
        if (src_process(st_, &d) != 0) return 0;     // src_process не аллоцирует
        return (size_t)d.output_frames_gen;
    }

private:
    SRC_STATE* st_ = nullptr;
    double ratio_ = 1.0;
    bool bypass_ = true;
};
