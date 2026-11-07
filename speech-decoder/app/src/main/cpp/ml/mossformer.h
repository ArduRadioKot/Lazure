#pragma once
// Этап 10: MossFormer(2) source separation, 16 kHz. Ожидается вход waveform [1,T] и 2 источника на выходе.
// Раскладка выхода определяется автоматически: [1,2,T], [1,T,2], [2,1,T] либо два выхода [1,T].
#include "ml/ort_common.h"
#include <cstring>

class MossFormer {
public:
    MossFormer(Ort::Env& env, const std::string& path, Ort::SessionOptions& so)
        : s_(env, path.c_str(), so),
          mem_(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault)) {
        in_ = inputName(s_, 0);
        for (size_t i = 0; i < s_.GetOutputCount(); ++i) outNames_.push_back(outputName(s_, i));
    }

    // out1/out2: буферы по n сэмплов.
    bool separate(const float* pcm, int n, float* out1, float* out2) {
        int64_t shape[] = {1, n};
        Ort::Value in = Ort::Value::CreateTensor<float>(mem_, const_cast<float*>(pcm), n, shape, 2);
        const char* iN[] = {in_.c_str()};
        std::vector<const char*> oN;
        for (auto& s : outNames_) oN.push_back(s.c_str());
        auto out = s_.Run(Ort::RunOptions{nullptr}, iN, &in, 1, oN.data(), oN.size());

        if (out.size() >= 2) {                                   // два отдельных выхода
            std::memcpy(out1, out[0].GetTensorData<float>(), n * sizeof(float));
            std::memcpy(out2, out[1].GetTensorData<float>(), n * sizeof(float));
            return true;
        }
        auto dims = out[0].GetTensorTypeAndShapeInfo().GetShape();
        const float* d = out[0].GetTensorData<float>();
        if (dims.size() != 3) return false;
        if (dims[1] == 2) {                                      // [B,2,T]
            std::memcpy(out1, d, n * sizeof(float));
            std::memcpy(out2, d + n, n * sizeof(float));
        } else if (dims[2] == 2) {                               // [B,T,2]
            for (int i = 0; i < n; ++i) { out1[i] = d[2 * i]; out2[i] = d[2 * i + 1]; }
        } else if (dims[0] == 2) {                               // [2,B,T]
            std::memcpy(out1, d, n * sizeof(float));
            std::memcpy(out2, d + n, n * sizeof(float));
        } else return false;
        return true;
    }

private:
    Ort::Session s_;
    Ort::MemoryInfo mem_;
    std::string in_;
    std::vector<std::string> outNames_;
};
