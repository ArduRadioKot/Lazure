#include "ml/dtln.h"
#include <cstring>
#include <cmath>

DtlnSuppressor::Io DtlnSuppressor::bind(Ort::Session& s) {
    Io io;
    for (size_t i = 0; i < s.GetInputCount(); ++i)
        (inputRank(s, i) == 4 ? io.stateIn : io.dataIn) = inputName(s, i);
    for (size_t i = 0; i < s.GetOutputCount(); ++i)
        (outputRank(s, i) == 4 ? io.stateOut : io.dataOut) = outputName(s, i);
    return io;
}

DtlnSuppressor::DtlnSuppressor(Ort::Env& env, const std::string& m1, const std::string& m2, Ort::SessionOptions& so)
    : s1_(env, m1.c_str(), so), s2_(env, m2.c_str(), so),
      mem_(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault)),
      fft_(kBlock),
      inBuf_(kBlock, 0.f), outBuf_(kBlock, 0.f),
      state1_(2 * 128 * 2, 0.f), state2_(2 * 128 * 2, 0.f),
      mag_(kBins), mask_(kBins), est_(kBlock), blk_(kBlock), c_(kBlock) {
    io1_ = bind(s1_);
    io2_ = bind(s2_);
}

void DtlnSuppressor::run(Ort::Session& s, const Io& io, float* data, const int64_t* shape, size_t rank,
                         size_t dataLen, std::vector<float>& state, float* outData, size_t outLen) {
    int64_t stShape[] = {1, 2, 128, 2};
    Ort::Value in[] = {
        Ort::Value::CreateTensor<float>(mem_, data, dataLen, shape, rank),
        Ort::Value::CreateTensor<float>(mem_, state.data(), state.size(), stShape, 4)};
    const char* iN[] = {io.dataIn.c_str(), io.stateIn.c_str()};
    const char* oN[] = {io.dataOut.c_str(), io.stateOut.c_str()};
    auto out = s.Run(Ort::RunOptions{nullptr}, iN, in, 2, oN, 2);
    std::memcpy(outData, out[0].GetTensorData<float>(), outLen * sizeof(float));
    std::memcpy(state.data(), out[1].GetTensorData<float>(), state.size() * sizeof(float));
}

void DtlnSuppressor::processHop(const float* in, float* out) {
    // 1) окно 512 со сдвигом 128
    std::memmove(inBuf_.data(), inBuf_.data() + kShift, (kBlock - kShift) * sizeof(float));
    std::memcpy(inBuf_.data() + kBlock - kShift, in, kShift * sizeof(float));

    // 2) rFFT -> магнитуда
    for (int i = 0; i < kBlock; ++i) c_[i] = Fft::C(inBuf_[i], 0.f);
    fft_.forward(c_.data());
    for (int k = 0; k < kBins; ++k) mag_[k] = std::abs(c_[k]);

    // 3) маска
    int64_t magShape[] = {1, 1, kBins};
    run(s1_, io1_, mag_.data(), magShape, 3, kBins, state1_, mask_.data(), kBins);

    // 4) применить маску к комплексному спектру и вернуться во времена (эрмитово достроить)
    for (int k = 0; k < kBins; ++k) c_[k] *= mask_[k];
    for (int k = 1; k < kBins - 1; ++k) c_[kBlock - k] = std::conj(c_[k]);
    fft_.inverse(c_.data());
    for (int i = 0; i < kBlock; ++i) est_[i] = c_[i].real();

    // 5) вторая модель (time-domain)
    int64_t estShape[] = {1, 1, kBlock};
    run(s2_, io2_, est_.data(), estShape, 3, kBlock, state2_, blk_.data(), kBlock);

    // 6) overlap-add
    std::memmove(outBuf_.data(), outBuf_.data() + kShift, (kBlock - kShift) * sizeof(float));
    std::memset(outBuf_.data() + kBlock - kShift, 0, kShift * sizeof(float));
    for (int i = 0; i < kBlock; ++i) outBuf_[i] += blk_[i];

    std::memcpy(out, outBuf_.data(), kShift * sizeof(float));
}
