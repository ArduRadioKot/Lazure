#pragma once
// Этап 4: High-Pass 80 Hz, Butterworth 2-го порядка (Q = 0.7071), RBJ, Transposed Direct Form II.
#include <cmath>
#include <cstddef>

class BiquadHpf {
public:
    void init(float fs, float fc = 80.f, float q = 0.70710678f) {
        const float w0 = 2.f * 3.14159265358979f * fc / fs;
        const float c = std::cos(w0), al = std::sin(w0) / (2.f * q);
        const float a0 = 1.f + al;
        b0_ = (1.f + c) * 0.5f / a0;
        b1_ = -(1.f + c) / a0;
        b2_ = b0_;
        a1_ = -2.f * c / a0;
        a2_ = (1.f - al) / a0;
        z1_ = z2_ = 0.f;
    }
    void process(float* x, size_t n) {
        for (size_t i = 0; i < n; ++i) {
            const float in = x[i];
            const float out = b0_ * in + z1_;
            z1_ = b1_ * in - a1_ * out + z2_;
            z2_ = b2_ * in - a2_ * out;
            x[i] = out;
        }
    }
private:
    float b0_ = 1, b1_ = 0, b2_ = 0, a1_ = 0, a2_ = 0, z1_ = 0, z2_ = 0;
};
