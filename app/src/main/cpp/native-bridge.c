#include <jni.h>
#include <string.h>
#include <android/log.h>

#define TAG "MadGammonJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_com_example_service_NativeNetworkBridge_nativeStartTun2Socks(
        JNIEnv *env,
        jobject thiz,
        jint tun_fd,
        jstring socks_host,
        jint socks_port) {
    const char *host = (*env)->GetStringUTFChars(env, socks_host, 0);
    LOGI("Starting native tun2socks: tunFd=%d, socks=%s:%d", tun_fd, host, socks_port);
    (*env)->ReleaseStringUTFChars(env, socks_host, host);
    return 0; // Return 0 on success
}

JNIEXPORT jint JNICALL
Java_com_example_service_NativeNetworkBridge_nativeStopTun2Socks(
        JNIEnv *env,
        jobject thiz) {
    LOGI("Stopping native tun2socks");
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_service_NativeNetworkBridge_nativeStartXray(
        JNIEnv *env,
        jobject thiz,
        jstring config_json) {
    const char *cfg = (*env)->GetStringUTFChars(env, config_json, 0);
    LOGI("Starting native Xray core with config length: %zu", strlen(cfg));
    (*env)->ReleaseStringUTFChars(env, config_json, cfg);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_service_NativeNetworkBridge_nativeStopXray(
        JNIEnv *env,
        jobject thiz) {
    LOGI("Stopping native Xray core");
    return 0;
}
