#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "whisper.h"

#define TAG "FlamboWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_flambo_recorder_stt_WhisperJNI_init(JNIEnv *env, jobject thiz, jstring modelPath_) {
    const char *modelPath = env->GetStringUTFChars(modelPath_, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(modelPath, cparams);
    env->ReleaseStringUTFChars(modelPath_, modelPath);
    if (!ctx) {
        LOGE("whisper_init_from_file failed: %s", modelPath);
    } else {
        LOGI("whisper init ok: %s", modelPath);
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_flambo_recorder_stt_WhisperJNI_free(JNIEnv *env, jobject thiz, jlong ctxPtr) {
    auto *ctx = reinterpret_cast<struct whisper_context*>(ctxPtr);
    if (ctx) whisper_free(ctx);
}

JNIEXPORT jint JNICALL
Java_com_flambo_recorder_stt_WhisperJNI_fullTranscribe(JNIEnv *env, jobject thiz, jlong ctxPtr, jint nThreads, jfloatArray audioData_, jstring lang_) {
    auto *ctx = reinterpret_cast<struct whisper_context*>(ctxPtr);
    if (!ctx) return -1;
    jfloat *audio = env->GetFloatArrayElements(audioData_, nullptr);
    jsize nSamples = env->GetArrayLength(audioData_);
    const char *lang = nullptr;
    std::string langStr;
    if (lang_ != nullptr) {
        const char *tmp = env->GetStringUTFChars(lang_, nullptr);
        langStr = tmp;
        env->ReleaseStringUTFChars(lang_, tmp);
        if (!langStr.empty() && langStr != "auto") {
            lang = langStr.c_str();
        } else {
            lang = nullptr;
        }
    }
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = true;
    params.print_special = false;
    params.translate = false;
    if (lang) params.language = lang;
    else params.language = "auto";
    params.n_threads = nThreads > 0 ? nThreads : 4;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    whisper_reset_timings(ctx);
    int ret = whisper_full(ctx, params, audio, nSamples);
    env->ReleaseFloatArrayElements(audioData_, audio, JNI_ABORT);
    if (ret != 0) {
        LOGE("whisper_full failed %d", ret);
    }
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_flambo_recorder_stt_WhisperJNI_getSegmentCount(JNIEnv *env, jobject thiz, jlong ctxPtr) {
    auto *ctx = reinterpret_cast<struct whisper_context*>(ctxPtr);
    if (!ctx) return 0;
    return whisper_full_n_segments(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_flambo_recorder_stt_WhisperJNI_getSegmentText(JNIEnv *env, jobject thiz, jlong ctxPtr, jint idx) {
    auto *ctx = reinterpret_cast<struct whisper_context*>(ctxPtr);
    if (!ctx) return env->NewStringUTF("");
    const char *text = whisper_full_get_segment_text(ctx, idx);
    return env->NewStringUTF(text ? text : "");
}

JNIEXPORT jlong JNICALL
Java_com_flambo_recorder_stt_WhisperJNI_getSegmentT0(JNIEnv *env, jobject thiz, jlong ctxPtr, jint idx) {
    auto *ctx = reinterpret_cast<struct whisper_context*>(ctxPtr);
    if (!ctx) return 0;
    return whisper_full_get_segment_t0(ctx, idx);
}

JNIEXPORT jlong JNICALL
Java_com_flambo_recorder_stt_WhisperJNI_getSegmentT1(JNIEnv *env, jobject thiz, jlong ctxPtr, jint idx) {
    auto *ctx = reinterpret_cast<struct whisper_context*>(ctxPtr);
    if (!ctx) return 0;
    return whisper_full_get_segment_t1(ctx, idx);
}

}
