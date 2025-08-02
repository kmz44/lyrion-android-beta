#include "llama.h"
#include "common.h"
#include "llm_inference.h"
#include <jni.h>

extern "C" {

JNIEXPORT jlong JNICALL Java_io_orabel_orabel_Orabel_loadModel(JNIEnv *env, jobject thiz, jstring model_path, jfloat min_p, jfloat temperature, jboolean store_chats) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);

    try {
        auto *inference = new LLMInference();
        inference->load_model(path, min_p, temperature, store_chats);
        env->ReleaseStringUTFChars(model_path, path);
        return reinterpret_cast<jlong>(inference);
    } catch (const std::exception &e) {
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }
}

JNIEXPORT void JNICALL Java_io_orabel_orabel_Orabel_addChatMessage(JNIEnv *env, jobject thiz, jlong instance_ptr, jstring message, jstring role) {
    if (instance_ptr != 0) {
        auto *inference = reinterpret_cast<LLMInference *>(instance_ptr);
        const char *msg = env->GetStringUTFChars(message, nullptr);
        const char *r = env->GetStringUTFChars(role, nullptr);
        inference->add_chat_message(msg, r);
        env->ReleaseStringUTFChars(message, msg);
        env->ReleaseStringUTFChars(role, r);
    }
}

JNIEXPORT void JNICALL Java_io_orabel_orabel_Orabel_startCompletion(JNIEnv *env, jobject thiz, jlong instance_ptr, jstring query) {
    if (instance_ptr != 0) {
        auto *inference = reinterpret_cast<LLMInference *>(instance_ptr);
        const char *q = env->GetStringUTFChars(query, nullptr);
        inference->start_completion(q);
        env->ReleaseStringUTFChars(query, q);
    }
}

JNIEXPORT jstring JNICALL Java_io_orabel_orabel_Orabel_completionLoop(JNIEnv *env, jobject thiz, jlong instance_ptr) {
    if (instance_ptr != 0) {
        auto *inference = reinterpret_cast<LLMInference *>(instance_ptr);
        std::string result = inference->completion_loop();
        return env->NewStringUTF(result.c_str());
    }
    return env->NewStringUTF("[ERROR]");
}

JNIEXPORT void JNICALL Java_io_orabel_orabel_Orabel_stopCompletion(JNIEnv *env, jobject thiz, jlong instance_ptr) {
    if (instance_ptr != 0) {
        auto *inference = reinterpret_cast<LLMInference *>(instance_ptr);
        inference->stop_completion();
    }
}

JNIEXPORT void JNICALL Java_io_orabel_orabel_Orabel_stopCompletionInternal(JNIEnv *env, jobject thiz, jlong instance_ptr) {
    if (instance_ptr != 0) {
        auto *inference = reinterpret_cast<LLMInference *>(instance_ptr);
        inference->stop_completion();
    }
}

JNIEXPORT void JNICALL Java_io_orabel_orabel_Orabel_cancelCompletion(JNIEnv *env, jobject thiz, jlong instance_ptr) {
    if (instance_ptr != 0) {
        auto *inference = reinterpret_cast<LLMInference *>(instance_ptr);
        inference->cancel_completion();
    }
}

JNIEXPORT void JNICALL Java_io_orabel_orabel_Orabel_cancelCompletionInternal(JNIEnv *env, jobject thiz, jlong instance_ptr) {
    if (instance_ptr != 0) {
        auto *inference = reinterpret_cast<LLMInference *>(instance_ptr);
        inference->cancel_completion();
    }
}

JNIEXPORT void JNICALL Java_io_orabel_orabel_Orabel_freeModel(JNIEnv *env, jobject thiz, jlong instance_ptr) {
    if (instance_ptr != 0) {
        auto *inference = reinterpret_cast<LLMInference *>(instance_ptr);
        delete inference;
    }
}

}
