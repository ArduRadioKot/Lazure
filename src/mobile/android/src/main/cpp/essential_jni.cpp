#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>
#include <vector>

#include "ggml.h"
#include "llama.h"

#if defined(ESSENTIAL_HAS_VULKAN)
#include "ggml-vulkan.h"
#endif

namespace {

std::once_flag g_backend_once;

void ensure_llama_backend() {
    std::call_once(g_backend_once, []() {
        llama_backend_init();
        ggml_backend_load_all();
    });
}

static bool abort_cb(void *userdata) {
    return *static_cast<bool *>(userdata);
}


static std::string token_to_piece(const llama_vocab *vocab, llama_token id) {
    char buf[256];
    int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
    if (n < 0) {
        std::vector<char> big(static_cast<size_t>(-n) + 8);
        n = llama_token_to_piece(vocab, id, big.data(), static_cast<int32_t>(big.size()), 0, true);
        if (n < 0) return "";
        return std::string(big.data(), static_cast<size_t>(n));
    }
    if (n == 0) return "";
    return std::string(buf, static_cast<size_t>(n));
}

struct Session {
    std::string model_path;
    int context_size = 4096;
    int n_threads = 4;
    int n_gpu_layers = 0;
    float temperature = 0.7f;
    float top_p = 0.95f;
    int max_tokens = 512;

    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *sampler = nullptr;

    bool aborted = false;
    bool stream_ok = false;
    int gen_count = 0;

    std::string token_char_cache;
};

static Session *ptr(jlong h) {
    return reinterpret_cast<Session *>(h);
}

static void free_sampler(Session *s) {
    if (s && s->sampler) {
        llama_sampler_free(s->sampler);
        s->sampler = nullptr;
    }
}

static void rebuild_sampler(Session *s) {
    free_sampler(s);
    auto sp = llama_sampler_chain_default_params();
    s->sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(s->top_p, 1));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(s->temperature));
    llama_sampler_chain_add(s->sampler, llama_sampler_init_dist(static_cast<uint32_t>(time(nullptr))));
}

#define LLM_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "EssentialLlm", __VA_ARGS__)
#define LLM_LOGW(...) __android_log_print(ANDROID_LOG_WARN, "EssentialLlm", __VA_ARGS__)


static bool is_valid_utf8(const char *string) {
    if (!string) return true;
    const auto *bytes = reinterpret_cast<const unsigned char *>(string);
    while (*bytes != 0x00) {
        int num = 0;
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes += 1;
        }
    }
    return true;
}


static size_t utf8_valid_prefix_len(const std::string &text) {
    const size_t len = text.size();
    if (len == 0) return 0;
    if (is_valid_utf8(text.c_str())) return len;
    for (size_t i = 1; i <= 4 && i <= len; ++i) {
        const unsigned char c = static_cast<unsigned char>(text[len - i]);
        if ((c & 0xE0) == 0xC0) {
            if (i < 2) return len - i;
        } else if ((c & 0xF0) == 0xE0) {
            if (i < 3) return len - i;
        } else if ((c & 0xF8) == 0xF0) {
            if (i < 4) return len - i;
        }
    }
    return 0;
}


static jstring take_cached_utf8(JNIEnv *env, Session *s, bool flush_partial) {
    if (!s || s->token_char_cache.empty()) {
        return env->NewStringUTF("");
    }
    size_t n = utf8_valid_prefix_len(s->token_char_cache);
    if (n == 0 && !flush_partial) {
        return env->NewStringUTF("");
    }
    if (n == 0) {
        s->token_char_cache.clear();
        return env->NewStringUTF("");
    }
    std::string chunk = s->token_char_cache.substr(0, n);
    s->token_char_cache.erase(0, n);
    if (!is_valid_utf8(chunk.c_str())) {
        LLM_LOGW("take_cached_utf8: invalid chunk after trim");
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(chunk.c_str());
}


static bool decode_prompt_tokens(Session *s, const std::vector<llama_token> &tokens) {
    if (!s || !s->ctx || tokens.empty()) return false;

    const uint32_t n_batch = llama_n_batch(s->ctx);
    const int32_t n_tokens = static_cast<int32_t>(tokens.size());
    int32_t pos = 0;
    const int64_t t_all = ggml_time_ms();

    while (pos < n_tokens) {
        if (s->aborted) return false;

        const int32_t chunk = std::min<int32_t>(
            n_tokens - pos,
            static_cast<int32_t>(n_batch));
        llama_batch batch = llama_batch_get_one(
            const_cast<llama_token *>(tokens.data()) + pos,
            chunk);

        const int64_t t0 = ggml_time_ms();
        if (llama_decode(s->ctx, batch) != 0) {
            LLM_LOGW("decode_prompt_tokens: decode failed at pos=%d chunk=%d", pos, chunk);
            return false;
        }
        LLM_LOGI("decode_prompt_tokens: pos=%d/%d chunk=%d in %lld ms",
                 pos, n_tokens, chunk, (long long)(ggml_time_ms() - t0));
        pos += chunk;
    }
    LLM_LOGI("decode_prompt_tokens: done %d tokens in %lld ms",
             n_tokens, (long long)(ggml_time_ms() - t_all));
    return true;
}

}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rassvet_essential_llama_LlamaNative_nativeHasGpuBackend(
        JNIEnv *,
        jobject) {
#if defined(ESSENTIAL_HAS_VULKAN)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rassvet_essential_llama_LlamaNative_nativeIsGpuAvailable(
        JNIEnv *,
        jobject) {
#if defined(ESSENTIAL_HAS_VULKAN)
    ensure_llama_backend();
    return ggml_backend_vk_get_device_count() > 0 ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rassvet_essential_llama_LlamaNative_nativeGpuDeviceDescription(
        JNIEnv *env,
        jobject) {
#if defined(ESSENTIAL_HAS_VULKAN)
    ensure_llama_backend();
    if (ggml_backend_vk_get_device_count() <= 0) {
        return env->NewStringUTF("");
    }
    char desc[256];
    ggml_backend_vk_get_device_description(0, desc, sizeof(desc));
    return env->NewStringUTF(desc);
#else
    return env->NewStringUTF("");
#endif
}

extern "C" JNIEXPORT jlong JNICALL Java_com_rassvet_essential_llama_LlamaNative_nativeLoad(
        JNIEnv *env,
        jobject,
        jstring path,
        jint context_size,
        jint n_threads,
        jint n_gpu_layers) {
    ensure_llama_backend();
    LLM_LOGI("nativeLoad start ctx=%d threads=%d gpu_layers=%d",
             context_size, n_threads, n_gpu_layers);

    Session *s = new Session();
    s->context_size = context_size > 0 ? context_size : 4096;
    s->n_threads = n_threads > 0 ? n_threads : 4;
    s->n_gpu_layers = n_gpu_layers;

    if (path == nullptr) {
        delete s;
        return 0;
    }
    const char *p = env->GetStringUTFChars(path, nullptr);
    if (!p || p[0] == '\0') {
        if (p) env->ReleaseStringUTFChars(path, p);
        delete s;
        return 0;
    }
    s->model_path = p;
    env->ReleaseStringUTFChars(path, p);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = n_gpu_layers > 0 ? n_gpu_layers : 0;

    s->model = llama_model_load_from_file(s->model_path.c_str(), mp);
    if (!s->model) {
        LLM_LOGW("nativeLoad: model file failed");
        delete s;
        return 0;
    }
    LLM_LOGI("nativeLoad: model ok");

    llama_context_params cp = llama_context_default_params();
    const int32_t n_ctx_train = llama_model_n_ctx_train(s->model);
    uint32_t n_ctx = static_cast<uint32_t>(std::max(512, s->context_size));
    if (n_ctx_train > 0) {
        n_ctx = std::min(n_ctx, static_cast<uint32_t>(n_ctx_train));
    }
    cp.n_ctx = n_ctx;

    uint32_t n_batch = 256U;
    if (n_ctx <= 768U) {
        n_batch = 64U;
    } else if (n_ctx >= 2048U) {
        n_batch = 512U;
    }
    cp.n_batch = std::min(n_batch, n_ctx);
    cp.no_perf = true;

    s->ctx = llama_init_from_model(s->model, cp);
    if (!s->ctx) {
        LLM_LOGW("nativeLoad: context init failed n_ctx=%u", n_ctx);
        llama_model_free(s->model);
        delete s;
        return 0;
    }

    llama_set_n_threads(s->ctx, s->n_threads, s->n_threads);
    LLM_LOGI("nativeLoad done n_ctx=%u n_batch=%u gpu_layers=%d",
             n_ctx, llama_n_batch(s->ctx), s->n_gpu_layers);


    const llama_vocab *warm_vocab = llama_model_get_vocab(s->model);
    llama_token warm_tok = llama_vocab_bos(warm_vocab);
    if (warm_tok == LLAMA_TOKEN_NULL) {
        warm_tok = 0;
    }
    llama_batch warm_batch = llama_batch_get_one(&warm_tok, 1);
    if (llama_decode(s->ctx, warm_batch) == 0) {
        LLM_LOGI("nativeLoad: warmup decode ok");
    }
    llama_memory_clear(llama_get_memory(s->ctx), true);
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT void JNICALL Java_com_rassvet_essential_llama_LlamaNative_nativeUnload(
        JNIEnv *,
        jobject,
        jlong handle) {
    Session *s = ptr(handle);
    if (!s) return;
    free_sampler(s);
    if (s->ctx) {
        llama_free(s->ctx);
        s->ctx = nullptr;
    }
    if (s->model) {
        llama_model_free(s->model);
        s->model = nullptr;
    }
    delete s;
}

extern "C" JNIEXPORT void JNICALL Java_com_rassvet_essential_llama_LlamaNative_nativeSetSampling(
        JNIEnv *,
        jobject,
        jlong handle,
        jfloat temperature,
        jfloat top_p,
        jint max_tokens) {
    Session *s = ptr(handle);
    if (!s) return;
    s->temperature = temperature;
    s->top_p = top_p;
    s->max_tokens = max_tokens > 0 ? max_tokens : 512;
}

extern "C" JNIEXPORT void JNICALL Java_com_rassvet_essential_llama_LlamaNative_nativeStreamStart(
        JNIEnv *env,
        jobject,
        jlong handle,
        jstring prompt) {
    Session *s = ptr(handle);
    if (!s || !s->model || !s->ctx) return;

    s->aborted = false;
    s->stream_ok = false;
    s->gen_count = 0;
    s->token_char_cache.clear();

    LLM_LOGI("nativeStreamStart");
    rebuild_sampler(s);
    llama_set_abort_callback(s->ctx, abort_cb, &s->aborted);
    llama_memory_clear(llama_get_memory(s->ctx), true);

    std::string p;
    if (prompt != nullptr) {
        const char *c = env->GetStringUTFChars(prompt, nullptr);
        if (c) {
            p = c;
            env->ReleaseStringUTFChars(prompt, c);
        }
    }

    const llama_vocab *vocab = llama_model_get_vocab(s->model);
    const int32_t n_tok = -llama_tokenize(vocab, p.c_str(), static_cast<int32_t>(p.size()), nullptr, 0, true, true);
    if (n_tok <= 0) {
        LLM_LOGW("nativeStreamStart: tokenize failed");
        return;
    }
    LLM_LOGI("nativeStreamStart: prompt_tokens=%d", n_tok);

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_tok));
    if (llama_tokenize(vocab, p.c_str(), static_cast<int32_t>(p.size()), prompt_tokens.data(),
                       static_cast<int32_t>(prompt_tokens.size()), true, true) < 0) {
        return;
    }

    const uint32_t n_ctx = llama_n_ctx(s->ctx);
    const size_t reserve = static_cast<size_t>(std::max(8, s->max_tokens)) + 8U;
    while (prompt_tokens.size() + reserve > n_ctx && !prompt_tokens.empty()) {
        prompt_tokens.erase(prompt_tokens.begin());
    }
    if (prompt_tokens.empty()) {
        LLM_LOGW("nativeStreamStart: prompt empty after trim");
        return;
    }

    const uint32_t n_batch = llama_n_batch(s->ctx);
    LLM_LOGI("nativeStreamStart: decode %zu tokens (n_batch=%u n_ctx=%u)",
             prompt_tokens.size(), n_batch, n_ctx);

    if (llama_model_has_encoder(s->model)) {
        const int32_t enc_n = static_cast<int32_t>(prompt_tokens.size());
        llama_batch enc_batch = llama_batch_get_one(prompt_tokens.data(), enc_n);
        if (llama_encode(s->ctx, enc_batch) != 0) {
            LLM_LOGW("nativeStreamStart: encode failed");
            return;
        }
        llama_token dec = llama_model_decoder_start_token(s->model);
        if (dec == LLAMA_TOKEN_NULL) {
            dec = llama_vocab_bos(vocab);
        }
        llama_batch dec_batch = llama_batch_get_one(&dec, 1);
        if (llama_decode(s->ctx, dec_batch) != 0) {
            LLM_LOGW("nativeStreamStart: decoder start failed");
            return;
        }
    } else if (!decode_prompt_tokens(s, prompt_tokens)) {
        LLM_LOGW("nativeStreamStart: decode failed");
        return;
    }

    s->stream_ok = true;
    LLM_LOGI("nativeStreamStart: prefill ok");
}

extern "C" JNIEXPORT jstring JNICALL Java_com_rassvet_essential_llama_LlamaNative_nativeStreamNext(
        JNIEnv *env,
        jobject,
        jlong handle) {
    Session *s = ptr(handle);
    if (!s || !s->ctx || !s->sampler || !s->stream_ok) {
        return env->NewStringUTF("");
    }
    if (s->aborted) {
        s->token_char_cache.clear();
        return env->NewStringUTF("");
    }
    if (s->gen_count >= s->max_tokens) {
        return take_cached_utf8(env, s, true);
    }

    const llama_vocab *vocab = llama_model_get_vocab(s->model);
    const llama_token new_id = llama_sampler_sample(s->sampler, s->ctx, -1);
    llama_sampler_accept(s->sampler, new_id);

    if (llama_vocab_is_eog(vocab, new_id)) {
        return take_cached_utf8(env, s, true);
    }

    const std::string out = token_to_piece(vocab, new_id);
    s->gen_count++;
    if (s->gen_count == 1 || (s->gen_count % 20) == 0) {
        LLM_LOGI("nativeStreamNext: gen_count=%d", s->gen_count);
    }

    llama_token tid = new_id;
    llama_batch next = llama_batch_get_one(&tid, 1);
    if (llama_decode(s->ctx, next) != 0) {
        s->stream_ok = false;
        s->token_char_cache.clear();
        return env->NewStringUTF("");
    }

    if (!out.empty()) {
        s->token_char_cache += out;
    }
    return take_cached_utf8(env, s, false);
}

extern "C" JNIEXPORT void JNICALL Java_com_rassvet_essential_llama_LlamaNative_nativeStreamAbort(
        JNIEnv *,
        jobject,
        jlong handle) {
    Session *s = ptr(handle);
    if (!s) return;
    s->aborted = true;
    s->token_char_cache.clear();
}

extern "C" JNIEXPORT jint JNICALL Java_com_rassvet_essential_llama_LlamaNative_nativeGetStreamTokenCount(
        JNIEnv *,
        jobject,
        jlong handle) {
    Session *s = ptr(handle);
    if (!s) return 0;
    return s->gen_count;
}


