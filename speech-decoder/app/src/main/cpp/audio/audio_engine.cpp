#include "audio/audio_engine.h"
#include <android/log.h>
#include <algorithm>
#include <cstring>
#include <thread>

#define TAG "AudioEngine"

bool AudioEngine::start() {
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Input)
     ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
     ->setSharingMode(oboe::SharingMode::Shared)
     ->setInputPreset(oboe::InputPreset::VoiceCommunication)   // Этап 1: системный AEC/NS, если есть в HAL
     ->setFormat(oboe::AudioFormat::Float)
     ->setFormatConversionAllowed(true)
     ->setChannelCount(oboe::ChannelCount::Unspecified)        // нативное число каналов
     ->setSessionId(oboe::SessionId::Allocate)                 // для AcousticEchoCanceler из Kotlin
     ->setDataCallback(shared_from_this())
     ->setErrorCallback(shared_from_this());
    // sampleRate не задаём: берём нативную частоту устройства

    if (b.openStream(stream_) != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "openStream failed");
        return false;
    }
    sr_ = stream_->getSampleRate();
    ch_ = stream_->getChannelCount();
    sessionId_ = static_cast<int>(stream_->getSessionId());
    __android_log_print(ANDROID_LOG_INFO, TAG, "sr=%d ch=%d fmt=%d hwfmt=%d session=%d",
                        sr_, ch_, (int)stream_->getFormat(), (int)stream_->getHardwareFormat(), sessionId_);

    if (!resampler_.init(sr_)) return false;
    hpf_.init(16000.f);
    mono_.assign(kSlice, 0.f);
    out_.assign(resampler_.outCapacity(kSlice), 0.f);

    running_ = true;
    return stream_->requestStart() == oboe::Result::OK;
}

void AudioEngine::stop() {
    running_ = false;
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream*, void* data, int32_t frames) {
    const float* in = static_cast<const float*>(data);
    int32_t done = 0;
    while (done < frames) {
        const int n = std::min<int32_t>(kSlice, frames - done);
        const float* src = in + (size_t)done * ch_;

        // Этап 2: downmix
        if (ch_ == 1) {
            std::memcpy(mono_.data(), src, n * sizeof(float));
        } else {
            const float inv = 1.f / ch_;
            for (int i = 0; i < n; ++i) {
                float s = 0.f;
                for (int c = 0; c < ch_; ++c) s += src[i * ch_ + c];
                mono_[i] = s * inv;
            }
        }
        // Этап 3: 16 kHz
        const size_t m = resampler_.process(mono_.data(), n, out_.data(), out_.size());
        // Этап 4: HPF 80 Hz
        hpf_.process(out_.data(), m);
        // Кольцо для DSP-воркера
        if (ring_.write(out_.data(), m) < m) overruns_.fetch_add(1, std::memory_order_relaxed);
        done += n;
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result r) {
    if (r == oboe::Result::ErrorDisconnected && running_) {      // например, гарнитура
        std::thread([self = shared_from_this()] {
            self->stop();
            self->start();                                        // ресемплер пересоздастся под новую частоту
        }).detach();
    }
}
