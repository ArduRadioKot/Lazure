#pragma once
// Детектор наложенной речи, чтобы не гонять тяжёлый MossFormer на каждом чанке.
// Модель: pyannote segmentation-3.0 (ONNX), вход [1,1,N], выход [1,F,7] (powerset):
//   0 — тишина, 1..3 — один спикер, 4..6 — пары (наложение).
// Если модель не загружена — возвращается defaultValue.
#include "ml/ort_common.h"
#include <memory>

class OverlapDetector {
public:
    OverlapDetector(Ort::Env& env, const std::string& pathOrEmpty, Ort::SessionOptions& so, bool defaultValue = false)
        : mem_(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault)), def_(defaultValue) {
        if (!pathOrEmpty.empty()) {
            s_ = std::make_unique<Ort::Session>(env, pathOrEmpty.c_str(), so);
            in_ = inputName(*s_, 0);
            out_ = outputName(*s_, 0);
        }
    }

    bool hasOverlap(const float* pcm, int n) {
        if (!s_) return def_;
        int64_t shape[] = {1, 1, n};
        Ort::Value in = Ort::Value::CreateTensor<float>(mem_, const_cast<float*>(pcm), n, shape, 3);
        const char* iN[] = {in_.c_str()};
        const char* oN[] = {out_.c_str()};
        auto out = s_->Run(Ort::RunOptions{nullptr}, iN, &in, 1, oN, 1);
        auto dims = out[0].GetTensorTypeAndShapeInfo().GetShape();   // [1,F,7]
        if (dims.size() != 3 || dims[2] != 7) return def_;
        const int F = (int)dims[1];
        const float* d = out[0].GetTensorData<float>();
        int ov = 0;
        for (int f = 0; f < F; ++f) {
            const float* r = d + (size_t)f * 7;
            int best = 0; for (int k = 1; k < 7; ++k) if (r[k] > r[best]) best = k;
            if (best >= 4) ++ov;
        }
        const float overlapSec = (float)ov / F * ((float)n / 16000.f);
        return overlapSec >= 0.3f;
    }

private:
    std::unique_ptr<Ort::Session> s_;
    Ort::MemoryInfo mem_;
    std::string in_, out_;
    bool def_;
};
