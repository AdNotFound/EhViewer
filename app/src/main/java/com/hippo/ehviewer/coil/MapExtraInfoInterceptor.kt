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

import android.graphics.Rect
import coil3.BitmapImage
import coil3.Image
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult

data class BitmapImageWithExtraInfo(
    val image: BitmapImage,
    val rect: Rect = Rect(0, 0, image.width, image.height),
    val hasQrCode: Boolean = false,
) : Image by image

object MapExtraInfoInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        val needMap = chain.request.detectQrCode
        if (needMap && result is SuccessResult) {
            val image = result.image
            if (image is BitmapImage) {
                return result.copy(image = BitmapImageWithExtraInfo(image))
            }
        }
        return result
    }
}


