#pragma once
// Диаризация: эмбеддинги спикера ECAPA-TDNN (192-мерные).
// ДОПУЩЕНИЕ: ONNX принимает waveform [1,N] (fbank внутри модели). Если ваш экспорт ждёт fbank-признаки,
// добавьте их расчёт перед Run (по аналогии с LogMel).
#include "ml/ort_common.h"
#include <vector>
#include <algorithm>

class Ecapa {
public:
    Ecapa(Ort::Env& env, const std::string& path, Ort::SessionOptions& so)
        : s_(env, path.c_str(), so),
          mem_(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault)) {
        in_ = inputName(s_, 0);
        out_ = outputName(s_, 0);
    }

    std::vector<float> embed(const float* pcm, int n) {
        // Минимум 1 с аудио: короткие реплики зацикливаем
        std::vector<float> buf(std::max(n, 16000));
        for (size_t i = 0; i < buf.size(); ++i) buf[i] = pcm[i % std::max(n, 1)];
        int64_t shape[] = {1, (int64_t)buf.size()};
        Ort::Value in = Ort::Value::CreateTensor<float>(mem_, buf.data(), buf.size(), shape, 2);
        const char* iN[] = {in_.c_str()};
        const char* oN[] = {out_.c_str()};
        auto out = s_.Run(Ort::RunOptions{nullptr}, iN, &in, 1, oN, 1);
        const size_t cnt = out[0].GetTensorTypeAndShapeInfo().GetElementCount();
        const float* d = out[0].GetTensorData<float>();
        return std::vector<float>(d, d + cnt);
    }

private:
    Ort::Session s_;
    Ort::MemoryInfo mem_;
    std::string in_, out_;
};
