#include <jni.h>
#include <string>
#include <vector>
#include <cstdlib>
#include <cstddef>
#include <cstring>
#include <thread>
#include <android/log.h>

#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_pranshulgg_recordmaster_core_stt_WhisperEngine_nativeLoadModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring model_path) {

    if (model_path == nullptr) {
        LOGE("nativeLoadModel: model_path is null");
        return 0LL;
    }

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) {
        LOGE("nativeLoadModel: GetStringUTFChars failed");
        return 0LL;
    }

    struct whisper_context_params cparams = whisper_context_default_params();
    // This build only ships the CPU backend, so never request a GPU backend.
    cparams.use_gpu = false;
    cparams.flash_attn = false;

    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        LOGE("nativeLoadModel: whisper_init_from_file_with_params failed");
        return 0LL;
    }

    LOGD("nativeLoadModel: context initialized");
    return (jlong)(uintptr_t)ctx;
}

JNIEXPORT jstring JNICALL
Java_com_pranshulgg_recordmaster_core_stt_WhisperEngine_nativeTranscribe(
        JNIEnv *env,
        jobject /* thiz */,
        jlong ctx_ptr,
        jshortArray pcm_array,
        jstring language) {

    if (ctx_ptr == 0LL) {
        LOGE("nativeTranscribe: invalid context");
        return env->NewStringUTF("");
    }
    if (pcm_array == nullptr) {
        LOGE("nativeTranscribe: pcm_array is null");
        return env->NewStringUTF("");
    }

    struct whisper_context *ctx = (struct whisper_context *)(uintptr_t)ctx_ptr;

    jsize len = env->GetArrayLength(pcm_array);
    if (len <= 0) {
        return env->NewStringUTF("");
    }

    std::vector<float> pcmf32((size_t)len);
    jshort *pcm = env->GetShortArrayElements(pcm_array, nullptr);
    if (pcm == nullptr) {
        LOGE("nativeTranscribe: GetShortArrayElements failed");
        return env->NewStringUTF("");
    }

    const float scale = 1.0f / 32768.0f;
    for (jsize i = 0; i < len; ++i) {
        pcmf32[(size_t)i] = (float)pcm[i] * scale;
    }
    env->ReleaseShortArrayElements(pcm_array, pcm, JNI_ABORT);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    // Output control
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;

    // Transcription behavior. Timestamps stay enabled so whisper can split long
    // audio into segments; they are not printed into the returned text.
    params.translate = false;
    params.single_segment = false;
    params.no_context = true;
    params.no_timestamps = false;

    // Language: an empty/null/"auto" tag means "detect and transcribe".
    // detect_language MUST stay false - when it is true whisper.cpp only
    // performs language detection and returns 0 segments (empty transcript).
    const char *language_chars = nullptr;
    if (language != nullptr) {
        language_chars = env->GetStringUTFChars(language, nullptr);
    }
    if (language_chars == nullptr || language_chars[0] == '\0' ||
        strcmp(language_chars, "auto") == 0) {
        params.language = nullptr;
    } else {
        params.language = language_chars;
    }
    params.detect_language = false;

    // Threading
    const unsigned int cores = std::thread::hardware_concurrency();
    params.n_threads = cores == 0 ? 4 : (int)(cores > 4 ? 4 : cores);

    // Avoid unnecessary callbacks
    params.new_segment_callback = nullptr;
    params.progress_callback = nullptr;
    params.encoder_begin_callback = nullptr;
    params.abort_callback = nullptr;

    const int rc = whisper_full(ctx, params, pcmf32.data(), (int)pcmf32.size());

    if (language != nullptr && language_chars != nullptr) {
        env->ReleaseStringUTFChars(language, language_chars);
    }

    if (rc != 0) {
        LOGE("nativeTranscribe: whisper_full failed with code %d", rc);
        return env->NewStringUTF("");
    }

    const int n_segments = whisper_full_n_segments(ctx);
    std::string result;
    result.clear();
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text != nullptr) {
            result += text;
        }
    }

    LOGD("nativeTranscribe: %d segment(s), %zu sample(s), %zu char(s)",
         n_segments, pcmf32.size(), result.size());

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_pranshulgg_recordmaster_core_stt_WhisperEngine_nativeFree(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong ctx_ptr) {

    if (ctx_ptr == 0LL) {
        return;
    }
    struct whisper_context *ctx = (struct whisper_context *)(uintptr_t)ctx_ptr;
    whisper_free(ctx);
    LOGD("nativeFree: context freed");
}

JNIEXPORT jstring JNICALL
Java_com_pranshulgg_recordmaster_core_stt_WhisperEngine_nativeInspect(
        JNIEnv *env,
        jobject /* thiz */,
        jstring model_path) {

    if (model_path == nullptr) {
        return nullptr;
    }

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) {
        LOGE("nativeInspect: GetStringUTFChars failed");
        return nullptr;
    }

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = false;

    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        LOGE("nativeInspect: model could not be loaded");
        return nullptr;
    }

    const char *type = whisper_model_type_readable(ctx);
    const bool multilingual = whisper_is_multilingual(ctx);

    std::string payload = std::string(type == nullptr ? "unknown" : type);
    payload += multilingual ? "|1" : "|0";

    whisper_free(ctx);

    LOGD("nativeInspect: %s", payload.c_str());
    return env->NewStringUTF(payload.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_pranshulgg_recordmaster_core_stt_WhisperEngine_nativeVersion(
        JNIEnv *env,
        jobject /* thiz */) {
    const char *ver = whisper_version();
    if (ver == nullptr) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(ver);
}

} // extern "C"
