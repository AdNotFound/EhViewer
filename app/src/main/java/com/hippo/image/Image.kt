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
import com.hippo.ehviewer.adblock.AdBlockManager
import com.hippo.ehviewer.coil.BitmapImageWithExtraInfo
import com.hippo.ehviewer.coil.analyzeAdFeatures
import com.hippo.ehviewer.coil.scanQrCode
import com.hippo.ehviewer.jni.getDHash
import com.hippo.ehviewer.jni.hasQrCode
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
    val image: CoilImage,
    private val src: ImageSource? = null,
) {
    private var mBitmap: Bitmap? = null
    private var mCanvas: Canvas? = null

    val animated get() = image is DrawableImage && image.drawable is Animatable
    val delay get() = if (animated) 40 else 0
    val isOpaque get() = false
    val width get() = image.width
    val height get() = image.height
    var frameUpdateAllowed = true
    var isRecycled = false
        private set
    var started = false
        private set

    @Synchronized
    fun recycle() {
        if (isRecycled) return
        when (image) {
            is DrawableImage -> {
                (image.drawable as? Animatable)?.stop()
                image.drawable.callback = null
            }

            is BitmapImageWithExtraInfo -> image.image.bitmap.recycle()

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
        val bitmap = when (image) {
            is BitmapImage -> image.bitmap

            is BitmapImageWithExtraInfo -> image.image.bitmap

            else -> {
                updateBitmap()
                mBitmap!!
            }
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

    fun start() {
        if (!started) {
            started = true
            if (image is DrawableImage) (image.drawable as? Animatable)?.start()
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

        private suspend fun decodeCoil(data: Any, analyzeFeatures: Boolean = false, blockOnQr: Boolean = false): CoilImage {
            val req = ImageRequest.Builder(appCtx).apply {
                data(data)
                size(sizeResolver)
                scale(Scale.FILL)
                precision(Precision.INEXACT)
                maxBitmapSize(Size.ORIGINAL)
                allowHardware(false)
                memoryCachePolicy(CachePolicy.DISABLED)
                if (analyzeFeatures) {
                    analyzeAdFeatures(true)
                    scanQrCode(blockOnQr)
                }
            }.build()
            return when (val result = appCtx.imageLoader.execute(req)) {
                is SuccessResult -> result.image
                is ErrorResult -> throw result.throwable
            }
        }

        suspend fun decode(src: ImageSource, analyzeFeatures: Boolean = false, blockOnQr: Boolean = false): Image? {
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
                                    return decode(source, analyzeFeatures, blockOnQr)
                                }
                            }
                        }
                        decodeCoil(src.source.uri, analyzeFeatures, blockOnQr)
                    }

                    is ByteBufferSource -> {
                        if (!isAtLeastU) {
                            rewriteGifSource(src.source)
                        }
                        decodeCoil(src.source, analyzeFeatures, blockOnQr)
                    }
                }

                // Check if QR code or dHash was detected and should be filtered
                if (analyzeFeatures && image is BitmapImageWithExtraInfo) {
                    val hasQr = image.hasQrCode
                    val hash = image.dHash
                    val isBlockedByHash = hash != 0L && AdBlockManager.isBlocked(hash)
                    val isBlockedByQr = blockOnQr && hasQr
                    if (isBlockedByQr || isBlockedByHash) {
                        Log.d("AdBlockDebug", "Image blocked: hasQr=$hasQr (autoBlock=$blockOnQr), dHash=${hash.toULong().toString(16)}, isBlockedByHash=$isBlockedByHash")
                        src.close()
                        throw AdDetectedException()
                    }
                }

                if (image is DrawableImage) {
                    image.drawable.setBounds(0, 0, image.drawable.intrinsicWidth, image.drawable.intrinsicHeight)
                }
                Image(image, src)
            }.onFailure {
                src.close()
                if (it is AdDetectedException) throw it
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

class AdDetectedException : Exception("Ad detected (QR code)")
