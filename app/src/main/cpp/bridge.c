#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <time.h>

#include "kate_core.h"
#include "audio/audio_stream.h"
#include "nlp/intent_parser.h"
#include "brain/habit_engine.h"
#include "brain/situation_engine.h"
#include "brain/emotion_engine.h"
#include "brain/decision_engine.h"
#include "ml/ml_inference.h"

// ================= GLOBALS =================

static JavaVM *g_vm = NULL;
static jobject g_bridge_obj = NULL;

#define MAX_APPS 128
static char app_list[MAX_APPS][128];
static int app_count = 0;

// ================= JNI =================

JNIEnv* get_jni_env() {
    JNIEnv *env;
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != 0) {
        return NULL;
    }
    return env;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_nativeInit(
        JNIEnv *env,
        jobject thiz,
        jstring modelPath) {
    const char *path = (*env)->GetStringUTFChars(env, modelPath, 0);
    tflite_init(path);
    (*env)->ReleaseStringUTFChars(env, modelPath, path);
}

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_loadHabits(
        JNIEnv *env,
        jobject thiz,
        jobjectArray habits
) {
    int len = (*env)->GetArrayLength(env, habits);

    for (int i = 0; i < len; i++) {

        jstring str = (jstring)(*env)->GetObjectArrayElement(env, habits, i);
        const char *nativeStr = (*env)->GetStringUTFChars(env, str, 0);

        // format: intent|entity|count
        char intent[32], entity[64];
        int count = 0;

        sscanf(nativeStr, "%31[^|]|%63[^|]|%d", intent, entity, &count);

        load_habit(intent, entity, count);

        (*env)->ReleaseStringUTFChars(env, str, nativeStr);
        (*env)->DeleteLocalRef(env, str);
    }
}
// ================= EVENT SYSTEM =================

void send_event(const char* type, const char* payload) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_bridge_obj) return;

    jclass cls = (*env)->GetObjectClass(env, g_bridge_obj);
    jmethodID method = (*env)->GetMethodID(
            env, cls,
            "onNativeEvent",
            "(Ljava/lang/String;Ljava/lang/String;)V"
    );

    if (!method) return;

    jstring jType = (*env)->NewStringUTF(env, type);
    jstring jPayload = (*env)->NewStringUTF(env, payload);

    (*env)->CallVoidMethod(env, g_bridge_obj, method, jType, jPayload);

    (*env)->DeleteLocalRef(env, jType);
    (*env)->DeleteLocalRef(env, jPayload);
}

// ================= AUDIO CONTROL =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_startAudio(JNIEnv *env, jobject thiz) {
    start_audio_stream();
}

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_stopAudio(JNIEnv *env, jobject thiz) {
    stop_audio_stream();
}

// ================= APP LIST =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_updateAppList(
        JNIEnv *env,
        jobject thiz,
        jobjectArray apps
) {
    app_count = 0;

    int len = (*env)->GetArrayLength(env, apps);

    for (int i = 0; i < len && i < MAX_APPS; i++) {
        jstring str = (jstring)(*env)->GetObjectArrayElement(env, apps, i);
        const char *nativeStr = (*env)->GetStringUTFChars(env, str, 0);

        strncpy(app_list[app_count], nativeStr, 127);
        app_list[app_count][127] = '\0';

        app_count++;

        (*env)->ReleaseStringUTFChars(env, str, nativeStr);
        (*env)->DeleteLocalRef(env, str);
    }
}

// ================= NLP PIPELINE =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_processText(
        JNIEnv *env,
        jobject thiz,
        jstring text
) {
    const char *nativeText = (*env)->GetStringUTFChars(env, text, 0);

    // 1. RULE-BASED NLP
    IntentResult result;
    parse_intent(nativeText, &result);

    // 2. ML PREDICTION
    const char* ml_intent = ml_predict_intent(nativeText);

    // 3. EMOTION
    float audio_energy = 0.03f;
    EmotionState emotion_state = detect_emotion(nativeText, audio_energy);
    const char* emotion = emotion_to_string(emotion_state);

    // 4. DECISION FUSION
    const char* final_intent = decide_intent(
            result.intent,
            ml_intent,
            result.entity,
            emotion
    );

    // 5. INJECT PREFERRED ENTITY IF MISSING
    if (strlen(result.entity) == 0) {
        const char* preferred = get_preferred_entity(final_intent);
        if (preferred != NULL) {
            strncpy(result.entity, preferred, 63);
            result.entity[63] = '\0';
        }
    }

    // 6. HABIT LEARNING
    record_habit(final_intent, result.entity);

    // 7. CONTEXT (SITUATION ENGINE)
    const char* adapted_intent =
            evaluate_situation(final_intent, result.entity);

    // 8. FINAL PAYLOAD
    char payload[192];
    snprintf(payload, sizeof(payload),
             "%s|%s|%s",
             adapted_intent,
             result.entity,
             emotion);

    // 9. EMIT
    send_event("INTENT", payload);

    (*env)->ReleaseStringUTFChars(env, text, nativeText);
}

// ================= SUGGESTIONS =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_requestSuggestion(
        JNIEnv *env,
        jobject thiz
) {
    time_t now = time(NULL);
    struct tm *t = localtime(&now);

    const char* suggestion = get_suggestion(t->tm_hour);

    if (suggestion) {
        send_event("SUGGESTION", suggestion);
    }
}
