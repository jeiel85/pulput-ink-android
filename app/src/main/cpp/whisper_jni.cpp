#include <jni.h>
#include <android/log.h>
#include <string>
#include <thread>
#include <algorithm>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_pulpit_ink_data_api_WhisperLib_initContext(
        JNIEnv *env, jobject /*thiz*/, jstring model_path_str) {

    const char *model_path = env->GetStringUTFChars(model_path_str, nullptr);
    LOGI("Initializing whisper context from %s", model_path);

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;

    struct whisper_context *context =
        whisper_init_from_file_with_params(model_path, cparams);

    env->ReleaseStringUTFChars(model_path_str, model_path);

    if (context == nullptr) {
        LOGE("whisper_init_from_file_with_params returned null");
        return 0;
    }
    LOGI("Whisper context ready: %p", context);
    return reinterpret_cast<jlong>(context);
}

JNIEXPORT void JNICALL
Java_io_pulpit_ink_data_api_WhisperLib_freeContext(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong context_ptr) {
    if (context_ptr == 0) return;
    auto *context = reinterpret_cast<struct whisper_context *>(context_ptr);
    whisper_free(context);
    LOGI("Whisper context freed");
}

JNIEXPORT jstring JNICALL
Java_io_pulpit_ink_data_api_WhisperLib_transcribeAudio(
        JNIEnv *env, jobject /*thiz*/,
        jlong context_ptr, jfloatArray pcm_data, jstring language_str) {

    if (context_ptr == 0) {
        return env->NewStringUTF("");
    }
    auto *context = reinterpret_cast<struct whisper_context *>(context_ptr);

    jfloat *pcm = env->GetFloatArrayElements(pcm_data, nullptr);
    const jsize pcm_length = env->GetArrayLength(pcm_data);
    const char *language = env->GetStringUTFChars(language_str, nullptr);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime  = false;
    params.print_progress  = false;
    params.print_timestamps = false;
    params.print_special   = false;
    params.translate       = false;
    params.language        = language;
    params.offset_ms       = 0;
    params.no_context      = true;
    params.single_segment  = false;

    int hw = (int) std::thread::hardware_concurrency();
    if (hw <= 0) hw = 2;
    params.n_threads = std::min(4, hw);

    LOGI("whisper_full: threads=%d lang=%s samples=%d",
         params.n_threads, language, (int)pcm_length);

    std::string result;
    if (whisper_full(context, params, pcm, pcm_length) != 0) {
        LOGE("whisper_full failed");
    } else {
        const int n = whisper_full_n_segments(context);
        LOGI("Inference done, %d segments", n);
        for (int i = 0; i < n; ++i) {
            const char *segment_text = whisper_full_get_segment_text(context, i);
            if (segment_text == nullptr) continue;
            if (!result.empty()) result += "\n\n";
            result += segment_text;
        }
    }

    env->ReleaseFloatArrayElements(pcm_data, pcm, JNI_ABORT);
    env->ReleaseStringUTFChars(language_str, language);

    return env->NewStringUTF(result.c_str());
}

} // extern "C"
