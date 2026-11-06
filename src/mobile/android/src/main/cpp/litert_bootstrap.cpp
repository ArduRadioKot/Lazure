#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

#define LOG_TAG "LiteRtBootstrap"

namespace {
bool g_opencl_sampler_ready = false;

void preload(const char* lib, bool required, bool* ready_flag) {
    dlerror();
    void* handle = dlopen(lib, RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        const char* err = dlerror();
        if (required) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to preload %s: %s", lib, err ? err : "?");
        } else {
            __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Optional preload %s: %s", lib, err ? err : "?");
        }
        if (ready_flag != nullptr) {
            *ready_flag = false;
        }
        return;
    }
    if (ready_flag != nullptr) {
        *ready_flag = true;
    }
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Preloaded %s", lib);
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    preload("libLiteRt.so", true, nullptr);
    preload("libLiteRtTopKOpenClSampler.so", false, &g_opencl_sampler_ready);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rassvet_essential_litert_LiteRtNativeBootstrap_nativeIsOpenClSamplerReady(
    JNIEnv* env,
    jclass clazz) {
    (void)env;
    (void)clazz;
    return g_opencl_sampler_ready ? JNI_TRUE : JNI_FALSE;
}


