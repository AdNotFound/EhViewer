/*
 * Copyright 2025 EhViewer
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * EhViewer. If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <android/bitmap.h>
#include "quirc.h"

JNIEXPORT jboolean JNICALL
Java_com_hippo_ehviewer_jni_QrCodeKt_hasQrCode(JNIEnv *env, jclass clazz, jobject bitmap) {
    AndroidBitmapInfo info;
    void *pixels;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return JNI_FALSE;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return JNI_FALSE;
    }

    struct quirc *qr = quirc_new();
    if (!qr) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    if (quirc_resize(qr, (int)info.width, (int)info.height) < 0) {
        quirc_destroy(qr);
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    int w, h;
    uint8_t *gray = quirc_begin(qr, &w, &h);

    // Convert ARGB_8888 to grayscale
    const uint8_t *src = (const uint8_t *)pixels;
    for (int y = 0; y < (int)info.height; y++) {
        for (int x = 0; x < (int)info.width; x++) {
            int src_idx = y * (int)info.stride + x * 4;
            // ARGB_8888 format on Android is actually RGBA in memory
            uint8_t r = src[src_idx];
            uint8_t g = src[src_idx + 1];
            uint8_t b = src[src_idx + 2];
            // Standard grayscale conversion
            gray[y * w + x] = (uint8_t)(0.299 * r + 0.587 * g + 0.114 * b);
        }
    }

    quirc_end(qr);
    AndroidBitmap_unlockPixels(env, bitmap);

    jboolean hasQr = quirc_count(qr) > 0 ? JNI_TRUE : JNI_FALSE;
    quirc_destroy(qr);

    return hasQr;
}
