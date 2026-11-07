#pragma once
// Этап 8: Silero VAD v5 (16 kHz): вход = 64 сэмпла контекста + 512 сэмплов кадра, state [2,1,128].
#include "ml/ort_common.h"
#include <cstring>

class SileroVad {
public:
    SileroVad(Ort::Env& env, const std::string& path, Ort::SessionOptions& so)
        : s_(env, path.c_str(), so),
          mem_(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault)) {}

    // onFrame(bool isSpeech) вызывается на каждый полный кадр из 512 сэмплов.
    template <class F>
    void feed(const float* x, size_t n, F&& onFrame) {
        for (size_t i = 0; i < n; ++i) {
            buf_[kCtx + fill_++] = x[i];
            if (fill_ == kFrame) {
                onFrame(infer() > thr_);
                std::memcpy(buf_, buf_ + kFrame, kCtx * sizeof(float));   // последние 64 → новый контекст
                fill_ = 0;
            }
        }
    }

private:
    static constexpr int kCtx = 64, kFrame = 512;

    float infer() {
        int64_t inS[] = {1, kCtx + kFrame}, stS[] = {2, 1, 128}, srS[] = {1}, sr = 16000;
        Ort::Value in[] = {
            Ort::Value::CreateTensor<float>(mem_, buf_, kCtx + kFrame, inS, 2),
            Ort::Value::CreateTensor<float>(mem_, state_, 256, stS, 3),
            Ort::Value::CreateTensor<int64_t>(mem_, &sr, 1, srS, 1)};
        const char* iN[] = {"input", "state", "sr"};
        const char* oN[] = {"output", "stateN"};
        auto o = s_.Run(Ort::RunOptions{nullptr}, iN, in, 3, oN, 2);
        std::memcpy(state_, o[1].GetTensorData<float>(), 256 * sizeof(float));
        return *o[0].GetTensorData<float>();
    }

    Ort::Session s_;
    Ort::MemoryInfo mem_;
    float buf_[kCtx + kFrame] = {};
    float state_[256] = {};
    int fill_ = 0;
    float thr_ = 0.5f;
};
