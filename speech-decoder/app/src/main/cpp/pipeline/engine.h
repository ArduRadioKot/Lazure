#pragma once
// Владелец всего пайплайна. Порядок членов важен для порядка уничтожения.
#include <android/log.h>
#include <fstream>
#include <functional>
#include <memory>
#include <string>
#include "audio/audio_engine.h"
#include "core/chunk_pool.h"
#include "ml/ort_common.h"
#include "pipeline/dsp_worker.h"
#include "pipeline/ml_orchestrator.h"

class SpeechEngine {
public:
    // Обязательные файлы: dtln_1.onnx, dtln_2.onnx, silero_vad.onnx, gigaam_ctc.onnx, vocab.txt, ecapa.onnx
    // Опциональные:       mossformer.onnx, overlap_seg.onnx
    static std::unique_ptr<SpeechEngine> create(const std::string& dir, TranscriptMerger::Cb cb) {
        for (const char* f : {"dtln_1.onnx", "dtln_2.onnx", "silero_vad.onnx", "gigaam_ctc.onnx", "vocab.txt", "ecapa.onnx"})
            if (!exists(dir + "/" + f)) {
                __android_log_print(ANDROID_LOG_ERROR, "SpeechEngine", "missing model: %s", f);
                return nullptr;
            }
        try {
            return std::unique_ptr<SpeechEngine>(new SpeechEngine(dir, std::move(cb)));
        } catch (const std::exception& e) {
            __android_log_print(ANDROID_LOG_ERROR, "SpeechEngine", "init failed: %s", e.what());
            return nullptr;
        }
    }

    bool start() {
        dsp_->start();
        ml_->start();
        return audio_->start();
    }
    void stop() {
        audio_->stop();
        dsp_->stop();
        ml_->stop();
    }
    int sessionId() const { return audio_->sessionId(); }

private:
    static bool exists(const std::string& p) { return std::ifstream(p).good(); }

    SpeechEngine(const std::string& dir, TranscriptMerger::Cb cb)
        : env_(ORT_LOGGING_LEVEL_WARNING, "speech"),
          soRt_(makeSessionOptions(1)),      // DSP-ветка: 1 поток
          soMl_(makeSessionOptions(3)),      // ML-ветка: 3 потока
          ring_(1 << 18),                    // ≈16 с на 16 kHz
          pool_(new ChunkPool()),
          merger_(std::move(cb)) {
        auto p = [&](const char* f) { return dir + "/" + f; };
        dtln_  = std::make_unique<DtlnSuppressor>(env_, p("dtln_1.onnx"), p("dtln_2.onnx"), soRt_);
        vad_   = std::make_unique<SileroVad>(env_, p("silero_vad.onnx"), soRt_);
        asr_   = std::make_unique<GigaAm>(env_, p("gigaam_ctc.onnx"), p("vocab.txt"), soMl_);
        ecapa_ = std::make_unique<Ecapa>(env_, p("ecapa.onnx"), soMl_);
        if (exists(p("mossformer.onnx"))) moss_ = std::make_unique<MossFormer>(env_, p("mossformer.onnx"), soMl_);
        overlap_ = std::make_unique<OverlapDetector>(env_, exists(p("overlap_seg.onnx")) ? p("overlap_seg.onnx") : "", soMl_, false);

        chunker_ = std::make_unique<Chunker>(*pool_);
        dsp_   = std::make_unique<DspWorker>(ring_, *dtln_, *vad_, *chunker_);
        ml_    = std::make_unique<MlOrchestrator>(*pool_, *overlap_, moss_.get(), *asr_, *ecapa_, merger_);
        audio_ = std::make_shared<AudioEngine>(ring_);
    }

    Ort::Env env_;
    Ort::SessionOptions soRt_, soMl_;
    SpscRing<float> ring_;
    std::unique_ptr<ChunkPool> pool_;
    TranscriptMerger merger_;
    std::unique_ptr<DtlnSuppressor> dtln_;
    std::unique_ptr<SileroVad> vad_;
    std::unique_ptr<GigaAm> asr_;
    std::unique_ptr<Ecapa> ecapa_;
    std::unique_ptr<MossFormer> moss_;
    std::unique_ptr<OverlapDetector> overlap_;
    std::unique_ptr<Chunker> chunker_;
    std::unique_ptr<DspWorker> dsp_;
    std::unique_ptr<MlOrchestrator> ml_;
    std::shared_ptr<AudioEngine> audio_;
};
