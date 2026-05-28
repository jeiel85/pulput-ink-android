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

static JavaVM *g_jvm = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

namespace {

// Glue passed through whisper_full's user_data so the C callback can hop back
// into the JVM. The Kotlin caller hands us a global ref to a ProgressCallback
// implementation; we cache its onProgress(int) method id once at setup.
struct JniProgressBridge {
    jobject callback_global_ref = nullptr;
    jmethodID on_progress_mid = nullptr;
    int last_reported = -1;
};

void native_progress_cb(struct whisper_context * /*ctx*/,
                        struct whisper_state * /*state*/,
                        int progress,
                        void *user_data) {
    auto *bridge = static_cast<JniProgressBridge *>(user_data);
    if (bridge == nullptr || bridge->callback_global_ref == nullptr || g_jvm == nullptr) {
        return;
    }
    // Throttle: only fire when the integer percent actually moves. Whisper invokes
    // this callback frequently and crossing JNI per-token would tank performance.
    if (progress == bridge->last_reported) return;
    bridge->last_reported = progress;

    JNIEnv *env = nullptr;
    bool attached = false;
    jint get = g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (get == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    } else if (get != JNI_OK || env == nullptr) {
        return;
    }
    env->CallVoidMethod(bridge->callback_global_ref, bridge->on_progress_mid,
                        static_cast<jint>(progress));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

} // namespace

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
        jlong context_ptr, jfloatArray pcm_data, jstring language_str,
        jobject progress_callback) {

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

    JniProgressBridge bridge;
    if (progress_callback != nullptr) {
        bridge.callback_global_ref = env->NewGlobalRef(progress_callback);
        jclass cb_class = env->GetObjectClass(progress_callback);
        bridge.on_progress_mid = env->GetMethodID(cb_class, "onProgress", "(I)V");
        env->DeleteLocalRef(cb_class);
        if (bridge.on_progress_mid != nullptr) {
            params.progress_callback = &native_progress_cb;
            params.progress_callback_user_data = &bridge;
        } else {
            LOGW("ProgressCallback object missing onProgress(I)V — running without progress");
            env->DeleteGlobalRef(bridge.callback_global_ref);
            bridge.callback_global_ref = nullptr;
        }
    }

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

    if (bridge.callback_global_ref != nullptr) {
        env->DeleteGlobalRef(bridge.callback_global_ref);
    }

    env->ReleaseFloatArrayElements(pcm_data, pcm, JNI_ABORT);
    env->ReleaseStringUTFChars(language_str, language);

    return env->NewStringUTF(result.c_str());
}

} // extern "C"
