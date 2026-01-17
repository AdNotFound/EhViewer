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

static int check_qr(struct quirc *qr) {
    int count = quirc_count(qr);
    for (int i = 0; i < count; i++) {
        struct quirc_code code;
        struct quirc_data data;
        quirc_extract(qr, i, &code);
        quirc_decode_error_t err = quirc_decode(&code, &data);
        if (err == QUIRC_SUCCESS || err == QUIRC_ERROR_DATA_ECC || err == QUIRC_ERROR_FORMAT_ECC) {
            return 1;
        }
    }
    return 0;
}

static void fill_image_scaled(struct quirc *qr, int width, int height, const uint8_t *src, int stride, int invert) {
    int w, h;
    uint8_t *gray = quirc_begin(qr, &w, &h);

    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            // 0.5x nearest neighbor downscaling
            int src_x = x * 2;
            int src_y = y * 2;
            int src_idx = src_y * stride + src_x * 4;
            
            uint8_t r = src[src_idx];
            uint8_t g = src[src_idx + 1];
            uint8_t b = src[src_idx + 2];
            uint8_t val = (uint8_t)(0.299 * r + 0.587 * g + 0.114 * b);
            if (invert) val = 255 - val;
            gray[y * w + x] = val;
        }
    }
    quirc_end(qr);
}

static void fill_image(struct quirc *qr, int width, int height, const uint8_t *src, int stride, int invert) {
    int w, h;
    uint8_t *gray = quirc_begin(qr, &w, &h);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int src_idx = y * stride + x * 4;
            uint8_t r = src[src_idx];
            uint8_t g = src[src_idx + 1];
            uint8_t b = src[src_idx + 2];
            uint8_t val = (uint8_t)(0.299 * r + 0.587 * g + 0.114 * b);
            if (invert) val = 255 - val;
            gray[y * w + x] = val;
        }
    }
    quirc_end(qr);
}

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

    // Pass 1 & 2: 0.5x scale (High performance, catches ad1, ad2, ad3)
    int sw = (int)info.width / 2;
    int sh = (int)info.height / 2;
    if (quirc_resize(qr, sw, sh) >= 0) {
        // Normal 0.5x
        fill_image_scaled(qr, info.width, info.height, (const uint8_t *)pixels, info.stride, 0);
        if (check_qr(qr)) {
            quirc_destroy(qr);
            AndroidBitmap_unlockPixels(env, bitmap);
            return JNI_TRUE;
        }
        // Inverted 0.5x
        fill_image_scaled(qr, info.width, info.height, (const uint8_t *)pixels, info.stride, 1);
        if (check_qr(qr)) {
            quirc_destroy(qr);
            AndroidBitmap_unlockPixels(env, bitmap);
            return JNI_TRUE;
        }
    }

    // Pass 3: 1.0x Normal scale (Fallback, catches ad4)
    if (quirc_resize(qr, (int)info.width, (int)info.height) >= 0) {
        fill_image(qr, info.width, info.height, (const uint8_t *)pixels, info.stride, 0);
        if (check_qr(qr)) {
            quirc_destroy(qr);
            AndroidBitmap_unlockPixels(env, bitmap);
            return JNI_TRUE;
        }
    }

    quirc_destroy(qr);
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_FALSE;
}
