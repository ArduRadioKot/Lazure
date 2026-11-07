#pragma once
// Этап 5: DTLN (stateful, 2 модели). Блок 512, сдвиг 128 (8 мс @16k), задержка ≈ 32 мс.
//   model_1: |STFT| [1,1,257] + state [1,2,128,2] -> mask [1,1,257] + state
//   model_2: est [1,1,512]    + state [1,2,128,2] -> out  [1,1,512] + state
// Имена тензоров определяются автоматически (state — тензор ранга 4, данные — ранга 3).
#include "ml/ort_common.h"
#include "ml/fft.h"

class DtlnSuppressor {
public:
    static constexpr int kBlock = 512, kShift = 128, kBins = 257;

    DtlnSuppressor(Ort::Env& env, const std::string& model1, const std::string& model2, Ort::SessionOptions& so);

    // in/out: ровно kShift сэмплов.
    void processHop(const float* in, float* out);

private:
    struct Io { std::string dataIn, stateIn, dataOut, stateOut; };
    static Io bind(Ort::Session& s);
    void run(Ort::Session& s, const Io& io, float* data, const int64_t* shape, size_t rank,
             size_t dataLen, std::vector<float>& state, float* outData, size_t outLen);

    Ort::Session s1_, s2_;
    Io io1_, io2_;
    Ort::MemoryInfo mem_;
    Fft fft_;
    std::vector<float> inBuf_, outBuf_, state1_, state2_, mag_, mask_, est_, blk_;
    std::vector<Fft::C> c_;
};
