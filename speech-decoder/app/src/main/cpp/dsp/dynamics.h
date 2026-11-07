#pragma once
// Этапы 6-7: RMS-нормализация (с шумовым гейтом) + мягкий компрессор + tanh-лимитер.
#include <cmath>
#include <algorithm>
#include <cstddef>

class Dynamics {
public:
    void init(float fs) {
        block_ = int(fs * 0.01f);                                   // окно RMS 10 мс
        auto k = [&](float tau) { return 1.f - std::exp(-1.f / (tau * fs)); };
        gDown_ = k(0.05f);  gUp_ = k(0.5f);                         // вниз быстро, вверх медленно
        eAtk_  = k(0.005f); eRel_ = k(0.1f);
        acc_ = 0.f; cnt_ = 0; target_ = gain_ = 1.f; env_ = 0.f;
    }

    void process(float* x, size_t n) {
        for (size_t i = 0; i < n; ++i) {
            acc_ += x[i] * x[i];
            if (++cnt_ == block_) {
                const float rms = std::sqrt(acc_ / block_);
                if (rms > kGate) target_ = std::clamp(kTargetRms / rms, kMinG, kMaxG);
                acc_ = 0.f; cnt_ = 0;
            }
            gain_ += (target_ - gain_) * (target_ < gain_ ? gDown_ : gUp_);
            float y = x[i] * gain_;

            const float a = std::fabs(y);                           // компрессор
            env_ += (a - env_) * (a > env_ ? eAtk_ : eRel_);
            if (env_ > kThr) y *= std::pow(env_ / kThr, 1.f / kRatio - 1.f);

            const float ay = std::fabs(y);                          // soft limiter
            if (ay > kKnee)
                y = std::copysign(kKnee + (1.f - kKnee) * std::tanh((ay - kKnee) / (1.f - kKnee)), y);
            x[i] = y;
        }
    }

private:
    static constexpr float kTargetRms = 0.1f;     // ≈ -20 dBFS
    static constexpr float kGate      = 0.0018f;  // ≈ -55 dBFS
    static constexpr float kMinG = 0.25f, kMaxG = 10.f;
    static constexpr float kThr = 0.35f, kRatio = 3.f, kKnee = 0.8f;
    int   block_ = 160, cnt_ = 0;
    float acc_ = 0, target_ = 1, gain_ = 1, env_ = 0;
    float gDown_ = 0, gUp_ = 0, eAtk_ = 0, eRel_ = 0;
};
