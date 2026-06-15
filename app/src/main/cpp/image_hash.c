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

    // Resize to 9x8 using box filter (average all source pixels per target cell)
    uint8_t gray[9 * 8];
    const uint8_t *src = (const uint8_t *)pixels;
    int sw = (int)info.width;
    int sh = (int)info.height;
    int stride = (int)info.stride;

    for (int ty = 0; ty < 8; ty++) {
        int y0 = ty * sh / 8;
        int y1 = (ty + 1) * sh / 8;
        if (y1 <= y0) y1 = y0 + 1;
        for (int tx = 0; tx < 9; tx++) {
            int x0 = tx * sw / 9;
            int x1 = (tx + 1) * sw / 9;
            if (x1 <= x0) x1 = x0 + 1;
            unsigned long sum = 0;
            int count = 0;
            for (int sy = y0; sy < y1 && sy < sh; sy++) {
                for (int sx = x0; sx < x1 && sx < sw; sx++) {
                    int idx = sy * stride + sx * 4;
                    sum += (unsigned long)(0.299f * src[idx] + 0.587f * src[idx + 1] + 0.114f * src[idx + 2]);
                    count++;
                }
            }
            gray[ty * 9 + tx] = (count > 0) ? (uint8_t)(sum / count) : 0;
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
