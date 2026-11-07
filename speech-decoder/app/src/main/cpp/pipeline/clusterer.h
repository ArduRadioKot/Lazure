#pragma once
// Онлайн-кластеризация эмбеддингов спикеров (косинусная близость + центроиды с EMA).
#include <vector>
#include <cmath>

class SpeakerClusterer {
public:
    using Emb = std::vector<float>;

    int assign(Emb e) {
        if (e.empty()) return 0;
        norm(e);
        int best = -1; float bs = -2.f;
        for (int k = 0; k < (int)c_.size(); ++k) {
            if (c_[k].size() != e.size()) continue;
            float s = 0; for (size_t i = 0; i < e.size(); ++i) s += c_[k][i] * e[i];
            if (s > bs) { bs = s; best = k; }
        }
        if (best >= 0 && bs >= kSame) {
            for (size_t i = 0; i < e.size(); ++i) c_[best][i] = 0.9f * c_[best][i] + 0.1f * e[i];
            norm(c_[best]);
            return best;
        }
        if ((int)c_.size() < kMaxSpk) { c_.push_back(e); return (int)c_.size() - 1; }
        return best < 0 ? 0 : best;
    }

private:
    static void norm(Emb& v) {
        float s = 0; for (float x : v) s += x * x;
        s = 1.f / std::sqrt(s + 1e-9f);
        for (float& x : v) x *= s;
    }
    static constexpr float kSame = 0.55f;     // стартовое значение — подбирайте на своих данных
    static constexpr int kMaxSpk = 8;
    std::vector<Emb> c_;
};
