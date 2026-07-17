// app/src/main/cpp/jni/native_bridge.cpp

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "kate_engine.h"

#define LOG_TAG "NativeBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace kate;

// Global references to callbacks
static jobject g_callbackObject = nullptr;
static jmethodID g_onTranscriptionMethod = nullptr;
static jmethodID g_onResponseMethod = nullptr;
static jmethodID g_onErrorMethod = nullptr;
static jmethodID g_onStateChangeMethod = nullptr;
static JavaVM* g_jvm = nullptr;

// JNI callback functions
void onTranscription(const std::string& text, bool isFinal) {
    if (!g_jvm || !g_callbackObject || !g_onTranscriptionMethod) return;
    
    JNIEnv* env;
    g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    
    if (env) {
        jstring jText = env->NewStringUTF(text.c_str());
        env->CallVoidMethod(g_callbackObject, g_onTranscriptionMethod, jText, isFinal);
        env->DeleteLocalRef(jText);
    }
}

void onResponse(const std::string& response) {
    if (!g_jvm || !g_callbackObject || !g_onResponseMethod) return;
    
    JNIEnv* env;
    g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    
    if (env) {
        jstring jResponse = env->NewStringUTF(response.c_str());
        env->CallVoidMethod(g_callbackObject, g_onResponseMethod, jResponse);
        env->DeleteLocalRef(jResponse);
    }
}

void onError(const std::string& error) {
    if (!g_jvm || !g_callbackObject || !g_onErrorMethod) return;
    
    JNIEnv* env;
    g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    
    if (env) {
        jstring jError = env->NewStringUTF(error.c_str());
        env->CallVoidMethod(g_callbackObject, g_onErrorMethod, jError);
        env->DeleteLocalRef(jError);
    }
}

void onStateChange(EngineState state) {
    if (!g_jvm || !g_callbackObject || !g_onStateChangeMethod) return;
    
    JNIEnv* env;
    g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    
    if (env) {
        env->CallVoidMethod(g_callbackObject, g_onStateChangeMethod, static_cast<jint>(state));
    }
}

// ==================== JNI EXPORTS ====================

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_dti_kate_core_NativeBridge_initializeEngine(
    JNIEnv* env,
    jobject thiz,
    jstring modelPath,
    jstring configPath
) {
    const char* model = env->GetStringUTFChars(modelPath, nullptr);
    const char* config = env->GetStringUTFChars(configPath, nullptr);
    
    bool result = KateEngine::getInstance().initialize(model, config);
    
    env->ReleaseStringUTFChars(modelPath, model);
    env->ReleaseStringUTFChars(configPath, config);
    
    return result;
}

JNIEXPORT void JNICALL
Java_com_dti_kate_core_NativeBridge_shutdownEngine(
    JNIEnv* env,
    jobject thiz
) {
    KateEngine::getInstance().shutdown();
}

JNIEXPORT jboolean JNICALL
Java_com_dti_kate_core_NativeBridge_startListening(
    JNIEnv* env,
    jobject thiz
) {
    return KateEngine::getInstance().startListening();
}

JNIEXPORT void JNICALL
Java_com_dti_kate_core_NativeBridge_stopListening(
    JNIEnv* env,
    jobject thiz
) {
    KateEngine::getInstance().stopListening();
}

JNIEXPORT jboolean JNICALL
Java_com_dti_kate_core_NativeBridge_isListening(
    JNIEnv* env,
    jobject thiz
) {
    return KateEngine::getInstance().isListening();
}

JNIEXPORT void JNICALL
Java_com_dti_kate_core_NativeBridge_feedAudio(
    JNIEnv* env,
    jobject thiz,
    jbyteArray audioData
) {
    jsize len = env->GetArrayLength(audioData);
    jbyte* data = env->GetByteArrayElements(audioData, nullptr);
    
    // Convert byte to int16 (2 bytes per sample)
    // Assuming audio is in little-endian PCM16 format
    int16_t* samples = reinterpret_cast<int16_t*>(data);
    size_t sampleCount = len / 2;
    
    KateEngine::getInstance().feedAudio(samples, sampleCount);
    
    env->ReleaseByteArrayElements(audioData, data, JNI_ABORT);
}

JNIEXPORT jstring JNICALL
Java_com_dti_kate_core_NativeBridge_processTranscription(
    JNIEnv* env,
    jobject thiz,
    jstring text
) {
    const char* textStr = env->GetStringUTFChars(text, nullptr);

    std::string response = KateEngine::getInstance().processTranscription(textStr);

    env->ReleaseStringUTFChars(text, textStr);

    return env->NewStringUTF(response.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dti_kate_core_NativeBridge_synthesizeSpeech(
    JNIEnv* env,
    jobject thiz,
    jstring text,
    jfloat tone
) {
    const char* textStr = env->GetStringUTFChars(text, nullptr);

    std::string result = KateEngine::getInstance().synthesizeSpeech(textStr, tone);

    env->ReleaseStringUTFChars(text, textStr);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dti_kate_core_NativeBridge_getCachedResponse(
    JNIEnv* env,
    jobject thiz,
    jstring query
) {
    const char* queryStr = env->GetStringUTFChars(query, nullptr);

    std::string result = KateEngine::getInstance().getCachedResponse(queryStr);

    env->ReleaseStringUTFChars(query, queryStr);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jint JNICALL
Java_com_dti_kate_core_NativeBridge_getEngineState(
    JNIEnv* env,
    jobject thiz
) {
    return static_cast<jint>(KateEngine::getInstance().getState());
}

JNIEXPORT void JNICALL
Java_com_dti_kate_core_NativeBridge_setVADThreshold(
    JNIEnv* env,
    jobject thiz,
    jfloat threshold
) {
    KateEngine::getInstance().setVADThreshold(threshold);
}

JNIEXPORT void JNICALL
Java_com_dti_kate_core_NativeBridge_setSilenceTimeout(
    JNIEnv* env,
    jobject thiz,
    jint ms
) {
    KateEngine::getInstance().setSilenceTimeout(ms);
}

JNIEXPORT void JNICALL
Java_com_dti_kate_core_NativeBridge_setMaxListeningTime(
    JNIEnv* env,
    jobject thiz,
    jint ms
) {
    KateEngine::getInstance().setMaxListeningTime(ms);
}

// Registers 'this' NativeBridge instance as the callback target.
// Kotlin's NativeBridge class implements:
//   fun onTranscription(text: String, isFinal: Boolean)
//   fun onResponse(response: String)
//   fun onError(error: String)
//   fun onStateChange(state: Int)
JNIEXPORT void JNICALL
Java_com_dti_kate_core_NativeBridge_setCallbacks(
    JNIEnv* env,
    jobject thiz,
    jobject callbackObject
) {
    env->GetJavaVM(&g_jvm);

    if (g_callbackObject) {
        env->DeleteGlobalRef(g_callbackObject);
        g_callbackObject = nullptr;
    }
    g_callbackObject = env->NewGlobalRef(callbackObject);

    jclass callbackClass = env->GetObjectClass(g_callbackObject);
    g_onTranscriptionMethod = env->GetMethodID(callbackClass, "onTranscription", "(Ljava/lang/String;Z)V");
    g_onResponseMethod = env->GetMethodID(callbackClass, "onResponse", "(Ljava/lang/String;)V");
    g_onErrorMethod = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");
    g_onStateChangeMethod = env->GetMethodID(callbackClass, "onStateChange", "(I)V");

    KateEngine::getInstance().setOnTranscription(onTranscription);
    KateEngine::getInstance().setOnResponse(onResponse);
    KateEngine::getInstance().setOnError(onError);
    KateEngine::getInstance().setOnStateChange(onStateChange);
}

JNIEXPORT void JNICALL
Java_com_dti_kate_core_NativeBridge_clearCallbacks(
    JNIEnv* env,
    jobject thiz
) {
    if (g_callbackObject) {
        env->DeleteGlobalRef(g_callbackObject);
        g_callbackObject = nullptr;
    }
    g_onTranscriptionMethod = nullptr;
    g_onResponseMethod = nullptr;
    g_onErrorMethod = nullptr;
    g_onStateChangeMethod = nullptr;
}

} // extern "C"
