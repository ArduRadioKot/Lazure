#pragma once
// Лог-мел признаки для GigaAM: 16 kHz, окно 400 (25 мс), шаг 160 (10 мс), 64 мел-фильтра, hann, power, HTK-мел.
// ВАЖНО: center / вид окна / нормировку сверьте с препроцессором вашего экспорта GigaAM.
#include <vector>
#include <cmath>
#include <algorithm>

class LogMel {
public:
    static constexpr int kWin = 400, kHop = 160, kMels = 64, kBins = kWin / 2 + 1;

    explicit LogMel(bool center = false) : center_(center), win_(kWin),
        cosT_((size_t)kBins * kWin), sinT_((size_t)kBins * kWin), fb_((size_t)kMels * kBins, 0.f) {
        for (int i = 0; i < kWin; ++i) win_[i] = 0.5f - 0.5f * std::cos(2.0 * M_PI * i / kWin);   // periodic hann
        for (int k = 0; k < kBins; ++k)
            for (int i = 0; i < kWin; ++i) {
                const double a = 2.0 * M_PI * k * i / kWin;
                cosT_[(size_t)k * kWin + i] = (float)std::cos(a);
                sinT_[(size_t)k * kWin + i] = (float)std::sin(a);
            }
        auto hz2mel = [](double f) { return 2595.0 * std::log10(1.0 + f / 700.0); };
        auto mel2hz = [](double m) { return 700.0 * (std::pow(10.0, m / 2595.0) - 1.0); };
        const double mLo = hz2mel(0), mHi = hz2mel(8000);
        std::vector<double> pts(kMels + 2);
        for (int i = 0; i < kMels + 2; ++i) pts[i] = mel2hz(mLo + (mHi - mLo) * i / (kMels + 1));
        for (int m = 0; m < kMels; ++m)
            for (int k = 0; k < kBins; ++k) {
                const double f = 8000.0 * k / (kBins - 1);
                const double up = (f - pts[m]) / (pts[m + 1] - pts[m]);
                const double dn = (pts[m + 2] - f) / (pts[m + 2] - pts[m + 1]);
                fb_[(size_t)m * kBins + k] = (float)std::max(0.0, std::min(up, dn));
            }
    }

    // out: [kMels][T] (мел-major). Возвращает T.
    int compute(const float* x, int n, std::vector<float>& out) const {
        std::vector<float> padded;
        if (center_) {                       // reflect-pad kWin/2 с обеих сторон
            padded.resize(n + kWin);
            for (int i = 0; i < kWin / 2; ++i) {
                padded[i] = x[std::min(kWin / 2 - i, n - 1)];
                padded[kWin / 2 + n + i] = x[std::max(n - 2 - i, 0)];
            }
            std::copy(x, x + n, padded.begin() + kWin / 2);
            x = padded.data(); n += kWin;
        }
        if (n < kWin) { out.clear(); return 0; }
        const int T = 1 + (n - kWin) / kHop;
        out.assign((size_t)kMels * T, 0.f);
        std::vector<float> fr(kWin), pw(kBins);
        for (int t = 0; t < T; ++t) {
            const float* s = x + (size_t)t * kHop;
            for (int i = 0; i < kWin; ++i) fr[i] = s[i] * win_[i];
            for (int k = 0; k < kBins; ++k) {
                const float* c = &cosT_[(size_t)k * kWin];
                const float* sn = &sinT_[(size_t)k * kWin];
                float re = 0.f, im = 0.f;
                for (int i = 0; i < kWin; ++i) { re += fr[i] * c[i]; im -= fr[i] * sn[i]; }
                pw[k] = re * re + im * im;
            }
            for (int m = 0; m < kMels; ++m) {
                const float* f = &fb_[(size_t)m * kBins];
                float acc = 0.f;
                for (int k = 0; k < kBins; ++k) acc += f[k] * pw[k];
                out[(size_t)m * T + t] = std::log(std::clamp(acc, 1e-9f, 1e9f));
            }
        }
        return T;
    }

private:
    bool center_;
    std::vector<float> win_, cosT_, sinT_, fb_;
};
