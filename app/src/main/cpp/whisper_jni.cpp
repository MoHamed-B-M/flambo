#include <jni.h>

#include "whisper.h"

// Phase 1 placeholder. Building this file proves that whisper.cpp and its
// vendored ggml compile and link into libwhisper-jni.so for every packaged
// ABI. The Kotlin-facing bridge (nativeInit, nativeLoadModel,
// nativeTranscribe, nativeFree) is added in Phase 2.
extern "C" JNIEXPORT jstring JNICALL
Java_com_pranshulgg_recordmaster_core_stt_WhisperEngine_nativeVersion(
        JNIEnv *env, jobject /* thiz */) {
    return env->NewStringUTF(whisper_version());
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * /* vm */, void * /* reserved */) {
    return JNI_VERSION_1_6;
}
