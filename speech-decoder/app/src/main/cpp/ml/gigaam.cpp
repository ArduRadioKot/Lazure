#include "ml/gigaam.h"
#include <fstream>
#include <algorithm>

GigaAm::GigaAm(Ort::Env& env, const std::string& modelPath, const std::string& vocabPath, Ort::SessionOptions& so)
    : s_(env, modelPath.c_str(), so),
      mem_(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault)),
      mel_(/*center=*/false) {
    std::ifstream f(vocabPath);
    std::string line;
    while (std::getline(f, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        vocab_.push_back(line);
    }
    inFeat_ = inputName(s_, 0);
    inLen_  = s_.GetInputCount() > 1 ? inputName(s_, 1) : "";
    outName_ = outputName(s_, 0);
}

std::vector<Word> GigaAm::transcribe(const float* pcm, int n) {
    std::vector<float> feats;
    const int T = mel_.compute(pcm, n, feats);
    if (T <= 0) return {};

    int64_t fShape[] = {1, LogMel::kMels, T};
    int64_t lShape[] = {1};
    int64_t len = T;
    std::vector<Ort::Value> in;
    std::vector<const char*> iN;
    in.push_back(Ort::Value::CreateTensor<float>(mem_, feats.data(), feats.size(), fShape, 3));
    iN.push_back(inFeat_.c_str());
    if (!inLen_.empty()) {
        in.push_back(Ort::Value::CreateTensor<int64_t>(mem_, &len, 1, lShape, 1));
        iN.push_back(inLen_.c_str());
    }
    const char* oN[] = {outName_.c_str()};
    auto out = s_.Run(Ort::RunOptions{nullptr}, iN.data(), in.data(), in.size(), oN, 1);

    auto shape = out[0].GetTensorTypeAndShapeInfo().GetShape();   // [1, T', V]
    const int Tp = (int)shape[1], V = (int)shape[2];
    const float* lp = out[0].GetTensorData<float>();
    const int blank = V - 1;

    // Greedy CTC + сборка слов с временем
    std::vector<Word> words;
    int prev = -1;
    bool inWord = false;
    for (int t = 0; t < Tp; ++t) {
        const float* row = lp + (size_t)t * V;
        const int id = (int)(std::max_element(row, row + V) - row);
        if (id != prev && id != blank && id < (int)vocab_.size()) {
            std::string tok = vocab_[id];
            bool startsWord = false;
            const std::string sp = "\xE2\x96\x81";                       // "▁"
            if (tok.compare(0, sp.size(), sp) == 0) { tok = tok.substr(sp.size()); startsWord = true; }
            if (tok == " ") { startsWord = true; tok.clear(); }
            if (startsWord) inWord = false;
            if (!tok.empty()) {
                if (!inWord || words.empty()) {
                    words.push_back({(int64_t)t * frameMs_, (int64_t)(t + 1) * frameMs_, tok});
                    inWord = true;
                } else {
                    words.back().text += tok;
                    words.back().endMs = (int64_t)(t + 1) * frameMs_;
                }
            }
        }
        prev = id;
    }
    return words;
}
