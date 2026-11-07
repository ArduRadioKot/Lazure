#pragma once
// Этап 11: GigaAM v3 (CTC, ONNX). Вход: log-mel [1,64,T] + длина; выход: log_probs [1,T',V], blank = V-1.
// vocab.txt: по одному токену в строке (индекс строки = id). "▁" трактуется как пробел/начало слова.
#include "ml/ort_common.h"
#include "ml/mel.h"
#include <cstdint>

struct Word { int64_t startMs, endMs; std::string text; };

class GigaAm {
public:
    GigaAm(Ort::Env& env, const std::string& modelPath, const std::string& vocabPath, Ort::SessionOptions& so);
    // pcm: 16 kHz mono. Время слов — от начала pcm.
    std::vector<Word> transcribe(const float* pcm, int n);

private:
    Ort::Session s_;
    Ort::MemoryInfo mem_;
    LogMel mel_;
    std::vector<std::string> vocab_;
    std::string inFeat_, inLen_, outName_;
    int frameMs_ = 40;     // 4x subsampling * 10 мс; проверьте на своём экспорте
};
