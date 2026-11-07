// Единственная точка JNI: create/start/stop/destroy + один callback на готовый сегмент.
#include <jni.h>
#include <android/log.h>
#include "pipeline/engine.h"

static JavaVM*   gVm = nullptr;
static jobject   gListener = nullptr;
static jmethodID gOnSeg = nullptr;

struct AttachGuard {
    JNIEnv* env = nullptr;
    bool own = false;
    AttachGuard() {
        if (gVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
            gVm->AttachCurrentThread(&env, nullptr);
            own = true;
        }
    }
    ~AttachGuard() { if (own) gVm->DetachCurrentThread(); }
};

static void emitSegment(const SegmentOut& s) {
    thread_local AttachGuard guard;            // прикреплён на всё время жизни ML-потока
    if (!guard.env || !gListener) return;
    jstring t = guard.env->NewStringUTF(s.text.c_str());
    guard.env->CallVoidMethod(gListener, gOnSeg, (jlong)s.startMs, (jlong)s.endMs, (jint)s.speaker, t);
    guard.env->DeleteLocalRef(t);
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    gVm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_speech_nativebridge_NativeSpeech_nativeCreate(JNIEnv* env, jobject, jstring dir, jobject listener) {
    if (gListener) { env->DeleteGlobalRef(gListener); gListener = nullptr; }
    gListener = env->NewGlobalRef(listener);
    jclass cls = env->GetObjectClass(listener);
    gOnSeg = env->GetMethodID(cls, "onSegment", "(JJILjava/lang/String;)V");

    const char* d = env->GetStringUTFChars(dir, nullptr);
    auto eng = SpeechEngine::create(d, emitSegment);
    env->ReleaseStringUTFChars(dir, d);
    return reinterpret_cast<jlong>(eng.release());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_speech_nativebridge_NativeSpeech_nativeStart(JNIEnv*, jobject, jlong h) {
    return h ? reinterpret_cast<SpeechEngine*>(h)->start() : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_speech_nativebridge_NativeSpeech_nativeStop(JNIEnv*, jobject, jlong h) {
    if (h) reinterpret_cast<SpeechEngine*>(h)->stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_speech_nativebridge_NativeSpeech_nativeDestroy(JNIEnv* env, jobject, jlong h) {
    delete reinterpret_cast<SpeechEngine*>(h);
    if (gListener) { env->DeleteGlobalRef(gListener); gListener = nullptr; }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_speech_nativebridge_NativeSpeech_nativeSessionId(JNIEnv*, jobject, jlong h) {
    return h ? reinterpret_cast<SpeechEngine*>(h)->sessionId() : -1;
}
