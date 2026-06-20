/*
 * Copyright 2022 Tarsin Norbin
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
package com.hippo.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.createBitmap
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.asImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import coil3.size.SizeResolver
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.jni.isGif
import com.hippo.ehviewer.jni.mmap
import com.hippo.ehviewer.jni.munmap
import com.hippo.ehviewer.jni.nativeTexImage
import com.hippo.ehviewer.jni.rewriteGifSource
import com.hippo.unifile.UniFile
import com.hippo.util.isAtLeastU
import java.nio.ByteBuffer
import coil3.Image as CoilImage

class Image private constructor(
    private val image: CoilImage,
    private val src: ImageSource? = null,
) {
    private var mBitmap: Bitmap? = null
    private var mCanvas: Canvas? = null

    val animated get() = image is DrawableImage && image.drawable is Animatable
    val delay get() = if (animated) 40 else 0
    val isOpaque get() = false
    val width get() = image.width
    val height get() = image.height
    var frameCallback: Runnable? = null
        private set
    var frameUpdateAllowed = true
    var isRecycled = false
        private set

    private val animDrawableCallback = object : Drawable.Callback {
        override fun invalidateDrawable(d: Drawable) {
            frameCallback?.run()
        }

        override fun scheduleDrawable(d: Drawable, what: Runnable, `when`: Long) {}
        override fun unscheduleDrawable(d: Drawable, what: Runnable) {}
    }

    @Synchronized
    fun recycle() {
        if (isRecycled) return
        when (image) {
            is DrawableImage -> {
                (image.drawable as? Animatable)?.stop()
                image.drawable.callback = null
            }

            is BitmapImage -> image.bitmap.recycle()
        }
        src?.close()
        mBitmap?.recycle()
        mBitmap = null
        mCanvas = null
        isRecycled = true
    }

    private fun prepareBitmap() {
        if (mBitmap != null) return
        mBitmap = createBitmap(width, height)
        mCanvas = Canvas(mBitmap!!)
    }

    private fun updateBitmap() {
        if (frameUpdateAllowed) {
            frameUpdateAllowed = false
            prepareBitmap()
            mCanvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            image.draw(mCanvas!!)
        }
    }

    fun texImage(init: Boolean, offsetX: Int, offsetY: Int, width: Int, height: Int) {
        val bitmap = if (image is BitmapImage) {
            image.bitmap
        } else {
            updateBitmap()
            mBitmap!!
        }
        nativeTexImage(
            bitmap,
            init,
            offsetX,
            offsetY,
            width,
            height,
        )
    }

    fun setFrameCallback(callback: Runnable?) {
        frameCallback = callback
        if (image is DrawableImage) {
            image.drawable.callback = if (callback != null) animDrawableCallback else null
        }
    }

    fun start() {
        if (image is DrawableImage) {
            (image.drawable as? Animatable)?.start()
        }
    }

    fun stop() {
        if (image is DrawableImage) {
            (image.drawable as? Animatable)?.stop()
        }
    }

    companion object {
        private val appCtx = EhApplication.application
        private val sizeResolver
            get() = with(appCtx.resources.displayMetrics) {
                val factor = when (Settings.readImageLimit) {
                    0 -> 0.75f
                    1 -> 1.0f
                    2 -> 1.33f
                    3 -> 1.5f
                    4 -> 2.0f
                    5 -> 3.0f
                    else -> 1.0f
                }
                val targetSize = (minOf(widthPixels, heightPixels) * factor).toInt()
                SizeResolver(Size(targetSize, targetSize))
            }

        private suspend fun decodeCoil(data: Any): CoilImage {
            val req = ImageRequest.Builder(appCtx).apply {
                data(data)
                size(sizeResolver)
                scale(Scale.FILL)
                precision(Precision.INEXACT)
                maxBitmapSize(Size.ORIGINAL)
                allowHardware(false)
                memoryCachePolicy(CachePolicy.DISABLED)
            }.build()
            return when (val result = appCtx.imageLoader.execute(req)) {
                is SuccessResult -> result.image
                is ErrorResult -> throw result.throwable
            }
        }

        suspend fun decode(src: ImageSource): Image? {
            return runCatching {
                val image = when (src) {
                    is UniFileSource -> {
                        if (!isAtLeastU) {
                            src.source.openFileDescriptor("rw").use {
                                val fd = it.fd
                                if (isGif(fd)) {
                                    val buffer = mmap(fd)!!
                                    val source = object : ByteBufferSource {
                                        override val source = buffer
                                        override fun close() {
                                            munmap(buffer)
                                            src.close()
                                        }
                                    }
                                    return decode(source)
                                }
                            }
                        }
                        decodeCoil(src.source.uri)
                    }

                    is ByteBufferSource -> {
                        if (!isAtLeastU) {
                            rewriteGifSource(src.source)
                        }
                        decodeCoil(src.source)
                    }
                }
                if (image is DrawableImage) {
                    image.drawable.setBounds(0, 0, image.drawable.intrinsicWidth, image.drawable.intrinsicHeight)
                }
                Image(image, src)
            }.onFailure {
                src.close()
                it.printStackTrace()
            }.getOrNull()
        }

        @JvmStatic
        fun create(bitmap: Bitmap): Image = Image(bitmap.asImage(), null)
    }
}

sealed interface ImageSource : AutoCloseable

interface UniFileSource : ImageSource {
    val source: UniFile
}

interface ByteBufferSource : ImageSource {
    val source: ByteBuffer
}
