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
#include <stdint.h>

JNIEXPORT jlong JNICALL
Java_com_hippo_ehviewer_jni_ImageHashKt_getDHash(JNIEnv *env, jclass clazz, jobject bitmap) {
    AndroidBitmapInfo info;
    void *pixels;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return 0;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return 0;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return 0;

    // Resize to 9x8 using a simple box filter approximation
    uint8_t gray[9 * 8];
    const uint8_t *src = (const uint8_t *)pixels;
    float x_step = (float)info.width / 9.0f;
    float y_step = (float)info.height / 8.0f;

    for (int y = 0; y < 8; y++) {
        for (int x = 0; x < 9; x++) {
            int sx = (int)(x * x_step);
            int sy = (int)(y * y_step);
            int src_idx = sy * (int)info.stride + sx * 4;
            uint8_t r = src[src_idx];
            uint8_t g = src[src_idx + 1];
            uint8_t b = src[src_idx + 2];
            gray[y * 9 + x] = (uint8_t)(0.299f * r + 0.587f * g + 0.114f * b);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    // Compute difference hash
    uint64_t hash = 0;
    for (int y = 0; y < 8; y++) {
        for (int x = 0; x < 8; x++) {
            if (gray[y * 9 + x] < gray[y * 9 + x + 1]) {
                hash |= (1ULL << (y * 8 + x));
            }
        }
    }

    return (jlong)hash;
}
