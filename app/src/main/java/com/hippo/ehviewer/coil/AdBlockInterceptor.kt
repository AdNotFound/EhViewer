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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.hippo.ehviewer.coil

import coil3.Extras
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.hippo.ehviewer.jni.hasQrCode
import com.hippo.ehviewer.jni.getDHash

private val analyzeAdFeaturesKey = Extras.Key(default = false)
private val scanQrCodeKey = Extras.Key(default = false)

fun ImageRequest.Builder.analyzeAdFeatures(enable: Boolean) = apply {
    extras[analyzeAdFeaturesKey] = enable
}

fun ImageRequest.Builder.scanQrCode(enable: Boolean) = apply {
    extras[scanQrCodeKey] = enable
}

val ImageRequest.analyzeAdFeatures: Boolean
    get() = getExtra(analyzeAdFeaturesKey)

val ImageRequest.scanQrCode: Boolean
    get() = getExtra(scanQrCodeKey)

object AdBlockInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        val request = chain.request
        if (request.analyzeAdFeatures && result is SuccessResult) {
            val image = result.image
            if (image is BitmapImageWithExtraInfo) {
                val bitmap = image.image.bitmap
                val hasQr = if (request.scanQrCode) hasQrCode(bitmap) else false
                val hash = getDHash(bitmap)
                val new = image.copy(hasQrCode = hasQr, dHash = hash)
                return result.copy(image = new)
            }
        }
        return result
    }
}
