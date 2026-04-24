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

// ================= GLOBALS =================

static JavaVM *g_vm = NULL;
static jobject g_bridge_obj = NULL;

#define MAX_APPS 128
 char app_list[MAX_APPS][128];
 int  app_count = 0;

// ================= JNI SETUP =================

JNIEnv* get_jni_env() {
    JNIEnv *env;
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != 0) return NULL;
    return env;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

// ================= EVENT SYSTEM =================

void send_event(const char* type, const char* payload) {
    JNIEnv *env = get_jni_env();
    if (!env || !g_bridge_obj) return;

    jclass cls = (*env)->GetObjectClass(env, g_bridge_obj);
    jmethodID method = (*env)->GetMethodID(env, cls,
        "onNativeEvent", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (!method) return;

    jstring jType    = (*env)->NewStringUTF(env, type);
    jstring jPayload = (*env)->NewStringUTF(env, payload);
    (*env)->CallVoidMethod(env, g_bridge_obj, method, jType, jPayload);
    (*env)->DeleteLocalRef(env, jType);
    (*env)->DeleteLocalRef(env, jPayload);
}

// ================= INIT =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_nativeInit(
        JNIEnv *env, jobject thiz) {
    g_bridge_obj = (*env)->NewGlobalRef(env, thiz);
}

// ================= AUDIO =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_startAudio(
        JNIEnv *env, jobject thiz) {
    start_audio_stream();
}

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_stopAudio(
        JNIEnv *env, jobject thiz) {
    stop_audio_stream();
}

// ================= APP LIST =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_updateAppList(
        JNIEnv *env, jobject thiz, jobjectArray apps) {
    app_count = 0;
    int len = (*env)->GetArrayLength(env, apps);
    for (int i = 0; i < len && i < MAX_APPS; i++) {
        jstring str = (jstring)(*env)->GetObjectArrayElement(env, apps, i);
        const char *s = (*env)->GetStringUTFChars(env, str, 0);
        strncpy(app_list[app_count], s, 127);
        app_list[app_count][127] = '\0';
        app_count++;
        (*env)->ReleaseStringUTFChars(env, str, s);
        (*env)->DeleteLocalRef(env, str);
    }
}

// ================= HABITS =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_loadHabits(
        JNIEnv *env, jobject thiz, jobjectArray habits) {
    int len = (*env)->GetArrayLength(env, habits);
    for (int i = 0; i < len; i++) {
        jstring str = (jstring)(*env)->GetObjectArrayElement(env, habits, i);
        const char *s = (*env)->GetStringUTFChars(env, str, 0);
        char intent[32], entity[64];
        int count = 0;
        sscanf(s, "%31[^|]|%63[^|]|%d", intent, entity, &count);
        load_habit(intent, entity, count);
        (*env)->ReleaseStringUTFChars(env, str, s);
        (*env)->DeleteLocalRef(env, str);
    }
}

// ================= NLP =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_processText(
        JNIEnv *env, jobject thiz, jstring text) {
    const char *nativeText = (*env)->GetStringUTFChars(env, text, 0);

    // 1. Rule-based NLP
    IntentResult result;
    parse_intent(nativeText, &result);

    // 2. Emotion from text
    float audio_energy = 0.03f;
    EmotionState emotion = detect_emotion(nativeText, audio_energy);
    const char* emotion_str = emotion_to_string(emotion);

    // 3. Decision fusion
    const char* final_intent = decide_intent(
        result.intent, result.intent, result.entity, emotion_str);

    // 4. Inject preferred entity if missing
    if (strlen(result.entity) == 0) {
        const char* preferred = get_preferred_entity(final_intent);
        if (preferred) {
            strncpy(result.entity, preferred, 63);
            result.entity[63] = '\0';
        }
    }

    // 5. Learn habit
    record_habit(final_intent, result.entity);

    // 6. Situation context
    const char* adapted = evaluate_situation(final_intent, result.entity);

    // 7. Emit to Kotlin
    char payload[192];
    snprintf(payload, sizeof(payload), "%s|%s|%s",
             adapted, result.entity, emotion_str);
    send_event("INTENT", payload);

    (*env)->ReleaseStringUTFChars(env, text, nativeText);
}

// ================= SUGGESTIONS =================

JNIEXPORT void JNICALL
Java_com_kate_assistant_bridge_KateBridge_requestSuggestion(
        JNIEnv *env, jobject thiz) {
    time_t now = time(NULL);
    struct tm *t = localtime(&now);
    const char* suggestion = get_suggestion(t->tm_hour);
    if (suggestion) send_event("SUGGESTION", suggestion);
}
