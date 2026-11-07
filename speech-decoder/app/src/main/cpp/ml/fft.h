#pragma once
// Минимальный radix-2 комплексный FFT (для DTLN, N=512). Таблицы строятся в конструкторе.
#include <complex>
#include <vector>
#include <cmath>

class Fft {
public:
    using C = std::complex<float>;
    explicit Fft(int n) : n_(n), tw_(n / 2), rev_(n) {
        for (int i = 0; i < n / 2; ++i) {
            const double a = -2.0 * M_PI * i / n;
            tw_[i] = C((float)std::cos(a), (float)std::sin(a));
        }
        int bits = 0; while ((1 << bits) < n) ++bits;
        for (int i = 0; i < n; ++i) {
            int r = 0; for (int b = 0; b < bits; ++b) if (i & (1 << b)) r |= 1 << (bits - 1 - b);
            rev_[i] = r;
        }
    }
    void forward(C* a) const {
        for (int i = 0; i < n_; ++i) if (i < rev_[i]) std::swap(a[i], a[rev_[i]]);
        for (int len = 2; len <= n_; len <<= 1) {
            const int half = len / 2, step = n_ / len;
            for (int i = 0; i < n_; i += len)
                for (int j = 0; j < half; ++j) {
                    const C u = a[i + j], v = a[i + j + half] * tw_[j * step];
                    a[i + j] = u + v;
                    a[i + j + half] = u - v;
                }
        }
    }
    void inverse(C* a) const {
        for (int i = 0; i < n_; ++i) a[i] = std::conj(a[i]);
        forward(a);
        const float s = 1.f / n_;
        for (int i = 0; i < n_; ++i) a[i] = std::conj(a[i]) * s;
    }
private:
    int n_;
    std::vector<C> tw_;
    std::vector<int> rev_;
};
