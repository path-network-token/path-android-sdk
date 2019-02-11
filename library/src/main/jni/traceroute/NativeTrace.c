#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <jni.h>
#include <assert.h>
#include <android/log.h>
#include "traceroute.h"

#define JNI_PACKAGE "network/path/mobilenode/library/data/runner/mtr/"
#define JNI_REG_CLASS JNI_PACKAGE "Mtr"
#define JNI_RESULT_CLASS JNI_PACKAGE "MtrResult"
#define JNI_SUMMARY_CLASS JNI_PACKAGE "MtrSummary"

JNIEXPORT jobject JNICALL native_trace(JNIEnv *env,
                                       jclass clazz,
                                       jstring server,
                                       jint port,
                                       jboolean resolve,
                                       jint maxHops,
                                       jint packetSize
) {
    jobject result = NULL;
    jobjectArray array = NULL;
    char **argv = NULL;
    int argc = 6;

    const char *c_str;
    c_str = (*env)->GetStringUTFChars(env, server, NULL);
    if (c_str == NULL) {
        goto complete;
    }

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "MTR: [%s:%d], resolve = %d, hops = %d, packetSize = %d", c_str,
                        port, resolve, maxHops, packetSize);

    if (resolve == JNI_FALSE) {
        argc += 1;
    }
    // after using it, remember to release the memory
    if (port != 0) {
        argc += 2;
    }
    argv = malloc(argc * sizeof(char *));
    if (argv == NULL) {
        goto complete;
    }

    for (int i = 0; i < argc; ++i) {
        argv[i] = malloc(255 * sizeof(char));
        memset(argv[i], 0, 255);
    }

    int argi = 0;
    // Executable name (+1)
    strcpy(argv[argi++], "traceroute");
    // IP4 setting (+1)
    strcpy(argv[argi++], "-4");
    // "Resolve" setting (+1)
    if (resolve == JNI_FALSE) {
        strcpy(argv[argi++], "-n");
    }
    // "Max hops" setting (+2)
    strcpy(argv[argi++], "-m");
    sprintf(argv[argi++], "%d", maxHops);

    // "Port" setting (+2)
    if (port != 0) {
        strcpy(argv[argi++], "-p");
        sprintf(argv[argi++], "%d", port);
    }

    // "Host" setting (+1)
    strcpy(argv[argi++], c_str);

    // "packetlen" setting (+1)
    sprintf(argv[argi++], "%d", packetSize);

    int count = 0;
    probe_result *results = NULL;
    char addr[INET6_ADDRSTRLEN];
    int res = traceroute(argc, argv, &count, &results, addr);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "MTR RESULT: %d (%s)", res, addr);
    if (res) {
        goto complete;
    }

    jclass cls = (*env)->FindClass(env, JNI_RESULT_CLASS);
    jmethodID constructorId = (*env)->GetMethodID(env, cls, "<init>",
                                                  "(ILjava/lang/String;Ljava/lang/String;ZDDDLjava/lang/String;)V");

    array = (*env)->NewObjectArray(env, count, cls, NULL);
    for (int i = 0; i < count; ++i) {
        probe_result *probe_res = &results[i];
        if (probe_res->ttl == 0) continue;

        if (probe_res->err[0]) {
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%2u %s (%s) %s",
                                probe_res->ttl,
                                probe_res->host,
                                probe_res->ip,
                                probe_res->err);
        } else if (probe_res->timeout) {
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%2u %s (%s) *",
                                probe_res->ttl,
                                probe_res->host,
                                probe_res->ip);
        } else {
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%2u %s (%s) %.3f ms",
                                probe_res->ttl,
                                probe_res->host,
                                probe_res->ip,
                                probe_res->delay * 1000.0);
        }

        jstring host = (*env)->NewStringUTF(env, probe_res->host);
        jstring ip = (*env)->NewStringUTF(env, probe_res->ip);
        // jstring ext = probe_res->err[0] ? (*env)->NewStringUTF(env, probe_res->ext) : NULL;
        jstring err = probe_res->ext[0] ? (*env)->NewStringUTF(env, probe_res->err) : NULL;
        jobject o = (*env)->NewObject(env, cls, constructorId,
                                      (jint) probe_res->ttl,
                                      host,
                                      ip,
                                      (jboolean) probe_res->timeout,
                                      (jdouble) probe_res->delay,
                                      (jdouble) probe_res->delay,
                                      (jdouble) probe_res->delay,
                                      err);
        (*env)->SetObjectArrayElement(env, array, i, o);
    }

    jclass summaryCls = (*env)->FindClass(env, JNI_SUMMARY_CLASS);
    jmethodID summaryConstructorId = (*env)->GetMethodID(env, summaryCls, "<init>",
                                                         "([L" JNI_RESULT_CLASS ";Ljava/lang/String;Ljava/lang/String;II)V");

    jstring targetIp = (*env)->NewStringUTF(env, addr);
    result = (*env)->NewObject(env,
                               summaryCls,
                               summaryConstructorId,
                               array,
                               server,
                               targetIp,
                               (jint) maxHops,
                               (jint) packetSize);

    complete:
    if (results != NULL) {
        free(results);
    }
    if (c_str != NULL) {
        (*env)->ReleaseStringUTFChars(env, server, c_str);
    }
    if (argv != NULL) {
        for (int i = 0; i < argc; ++i) {
            if (argv[i] != NULL) {
                free(argv[i]);
                argv[i] = NULL;
            }
        }
        free(argv);
    }
    return result;
}


static JNINativeMethod gMethods[] = {
        {"trace", "(Ljava/lang/String;IZII)L" JNI_SUMMARY_CLASS ";", (void *) native_trace},
};

static int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *gMethods,
                                 int numMethods) {
    jclass clazz;
    clazz = (*env)->FindClass(env, className);
    if (clazz == NULL) {
        return JNI_FALSE;
    }
    jint res = (*env)->RegisterNatives(env, clazz, gMethods, numMethods);
    if (res < 0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static int registerNatives(JNIEnv *env) {
    if (!registerNativeMethods(env, JNI_REG_CLASS, gMethods,
                               sizeof(gMethods) / sizeof(gMethods[0]))) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jint result = -1;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return -1;
    }
    assert(env != NULL);
    if (!registerNatives(env)) {
        return -1;
    }
    result = JNI_VERSION_1_4;
    return result;
}
