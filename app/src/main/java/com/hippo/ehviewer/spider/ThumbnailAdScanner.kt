package com.hippo.ehviewer.spider

import android.content.Context
import android.graphics.Bitmap
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.hippo.ehviewer.adblock.AdBlockManager
import com.hippo.ehviewer.client.data.GalleryPreview
import com.hippo.ehviewer.coil.BitmapImageWithExtraInfo
import com.hippo.ehviewer.jni.getDHash
import com.hippo.ehviewer.jni.hasQrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailAdScanner {
    internal fun canClip(preview: GalleryPreview, bitmapWidth: Int, bitmapHeight: Int): Boolean = preview.hasClipAspect() &&
        preview.offsetX >= 0 && preview.offsetY >= 0 &&
        preview.offsetX + preview.clipWidth <= bitmapWidth &&
        preview.offsetY + preview.clipHeight <= bitmapHeight

    suspend fun scanPreview(context: Context, preview: GalleryPreview): Boolean = withContext(Dispatchers.IO) {
        val url = preview.imageUrl?.takeIf { it.isNotBlank() } ?: return@withContext false
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false) // Required for JNI AndroidBitmap_lockPixels
            .build()
        val result = context.imageLoader.execute(request)
        val image = result.image ?: return@withContext false
        val originalBitmap = (image as? BitmapImageWithExtraInfo)?.image?.bitmap
            ?: (image as? BitmapImage)?.bitmap
            ?: runCatching { image.toBitmap() }.getOrNull()
            ?: return@withContext false

        if (originalBitmap.isRecycled) return@withContext false

        val targetBitmap = if (canClip(preview, originalBitmap.width, originalBitmap.height)) {
            Bitmap.createBitmap(
                originalBitmap,
                preview.offsetX,
                preview.offsetY,
                preview.clipWidth,
                preview.clipHeight,
            )
        } else {
            originalBitmap
        }

        try {
            val hash = getDHash(targetBitmap)
            if (hash != 0L && AdBlockManager.isBlocked(hash)) {
                return@withContext true
            }

            val hasQr = hasQrCode(targetBitmap)
            if (hasQr) {
                if (hash != 0L) {
                    AdBlockManager.addHash(hash)
                }
                return@withContext true
            }
            false
        } finally {
            if (targetBitmap !== originalBitmap) {
                targetBitmap.recycle()
            }
        }
    }
}
