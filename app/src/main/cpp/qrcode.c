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

static inline uint8_t get_gray(const uint8_t *src, int x, int y, int stride, int format) {
    if (format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        int idx = y * stride + x * 4;
        uint8_t r = src[idx];
        uint8_t g = src[idx + 1];
        uint8_t b = src[idx + 2];
        return (uint8_t)(0.299f * r + 0.587f * g + 0.114f * b);
    } else if (format == ANDROID_BITMAP_FORMAT_RGB_565) {
        const uint16_t *pixel_ptr = (const uint16_t *)(src + y * stride + x * 2);
        uint16_t p = *pixel_ptr;
        uint8_t r = (p >> 11) << 3;
        uint8_t g = ((p >> 5) & 0x3f) << 2;
        uint8_t b = (p & 0x1f) << 3;
        return (uint8_t)(0.299f * r + 0.587f * g + 0.114f * b);
    }
    return 0;
}

static void fill_image_scaled(struct quirc *qr, int width, int height, const uint8_t *src, int stride, int format, int invert) {
    int w, h;
    uint8_t *gray = quirc_begin(qr, &w, &h);

    for (int y = 0; y < h; y++) {
        int src_y = y * 2;
        if (src_y >= height) src_y = height - 1;
        for (int x = 0; x < w; x++) {
            int src_x = x * 2;
            if (src_x >= width) src_x = width - 1;
            uint8_t val = get_gray(src, src_x, src_y, stride, format);
            if (invert) val = 255 - val;
            gray[y * w + x] = val;
        }
    }
    quirc_end(qr);
}

static void fill_image(struct quirc *qr, int width, int height, const uint8_t *src, int stride, int format, int invert) {
    int w, h;
    uint8_t *gray = quirc_begin(qr, &w, &h);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            uint8_t val = get_gray(src, x, y, stride, format);
            if (invert) val = 255 - val;
            gray[y * w + x] = val;
        }
    }
    quirc_end(qr);
}

static void fill_image_2x(struct quirc *qr, int width, int height, const uint8_t *src, int stride, int format, int invert) {
    int w, h;
    uint8_t *gray = quirc_begin(qr, &w, &h);

    for (int y = 0; y < h; y++) {
        int src_y = y / 2;
        if (src_y >= height) src_y = height - 1;
        for (int x = 0; x < w; x++) {
            int src_x = x / 2;
            if (src_x >= width) src_x = width - 1;
            uint8_t val = get_gray(src, src_x, src_y, stride, format);
            if (invert) val = 255 - val;
            gray[y * w + x] = val;
        }
    }
    quirc_end(qr);
}

static void fill_image_threshold(struct quirc *qr, int width, int height, const uint8_t *src, int stride, int format, int scale, int th) {
    int w, h;
    uint8_t *gray = quirc_begin(qr, &w, &h);

    for (int y = 0; y < h; y++) {
        int src_y = y / scale;
        if (src_y >= height) src_y = height - 1;
        for (int x = 0; x < w; x++) {
            int src_x = x / scale;
            if (src_x >= width) src_x = width - 1;
            uint8_t val = get_gray(src, src_x, src_y, stride, format);
            gray[y * w + x] = (val < th) ? 0 : 255;
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
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 && info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
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

    int width = (int)info.width;
    int height = (int)info.height;
    int format = info.format;
    const uint8_t *src_pixels = (const uint8_t *)pixels;

    // Small image / thumbnail strategy: DO NOT downsample, scan 1.0x and upscale 2.0x
    if (width < 600 && height < 600) {
        // Pass 1: 1.0x normal
        if (quirc_resize(qr, width, height) >= 0) {
            fill_image(qr, width, height, src_pixels, info.stride, format, 0);
            if (check_qr(qr)) {
                quirc_destroy(qr);
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_TRUE;
            }
            // Pass 2: 1.0x inverted
            fill_image(qr, width, height, src_pixels, info.stride, format, 1);
            if (check_qr(qr)) {
                quirc_destroy(qr);
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_TRUE;
            }
        }

        // Pass 3: 2.0x NN upscale normal
        if (quirc_resize(qr, width * 2, height * 2) >= 0) {
            fill_image_2x(qr, width, height, src_pixels, info.stride, format, 0);
            if (check_qr(qr)) {
                quirc_destroy(qr);
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_TRUE;
            }
            // Pass 4: 2.0x NN upscale inverted
            fill_image_2x(qr, width, height, src_pixels, info.stride, format, 1);
            if (check_qr(qr)) {
                quirc_destroy(qr);
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_TRUE;
            }
            // Pass 5: 2.0x with targeted thresholding to overcome Otsu background bias
            int ths[] = {100, 120, 140};
            for (int t = 0; t < 3; t++) {
                fill_image_threshold(qr, width, height, src_pixels, info.stride, format, 2, ths[t]);
                if (check_qr(qr)) {
                    quirc_destroy(qr);
                    AndroidBitmap_unlockPixels(env, bitmap);
                    return JNI_TRUE;
                }
            }
        }
    } else {

        // Large image / full page strategy: 0.5x fast path first, then 1.0x fallback
        int sw = width / 2;
        int sh = height / 2;
        if (quirc_resize(qr, sw, sh) >= 0) {
            // Normal 0.5x
            fill_image_scaled(qr, width, height, src_pixels, info.stride, format, 0);
            if (check_qr(qr)) {
                quirc_destroy(qr);
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_TRUE;
            }
            // Inverted 0.5x
            fill_image_scaled(qr, width, height, src_pixels, info.stride, format, 1);
            if (check_qr(qr)) {
                quirc_destroy(qr);
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_TRUE;
            }
        }

        // 1.0x Fallback
        if (quirc_resize(qr, width, height) >= 0) {
            fill_image(qr, width, height, src_pixels, info.stride, format, 0);
            if (check_qr(qr)) {
                quirc_destroy(qr);
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_TRUE;
            }
            fill_image(qr, width, height, src_pixels, info.stride, format, 1);
            if (check_qr(qr)) {
                quirc_destroy(qr);
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_TRUE;
            }
        }
    }

    quirc_destroy(qr);
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_FALSE;
}
