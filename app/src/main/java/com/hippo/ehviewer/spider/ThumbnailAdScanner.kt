package com.hippo.ehviewer.spider

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.SizeResolver
import coil3.toBitmap
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.adblock.AdBlockManager
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.GalleryPreview
import com.hippo.ehviewer.coil.BitmapImageWithExtraInfo
import com.hippo.ehviewer.coil.loadReaderPreviewCache
import com.hippo.ehviewer.coil.saveReaderPreviewCache
import com.hippo.ehviewer.jni.getDHash
import com.hippo.ehviewer.jni.hasQrCode
import com.hippo.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AdBlockEvent(
    val gid: Long,
    val pageIndex: Int,
    val isBlocked: Boolean = true,
)

object ThumbnailAdScanner {
    private val _adBlockEvents = MutableSharedFlow<AdBlockEvent>(extraBufferCapacity = 64)
    val adBlockEvents = _adBlockEvents.asSharedFlow()

    fun notifyAdBlocked(gid: Long, pageIndex: Int, isBlocked: Boolean = true, token: String? = null) {
        _adBlockEvents.tryEmit(AdBlockEvent(gid, pageIndex, isBlocked))
        if (!token.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                loadReaderPreviewCache(gid, token)?.let { cache ->
                    cache[pageIndex]?.let { p ->
                        if (p.isAdBlocked != isBlocked) {
                            p.isAdBlocked = isBlocked
                            saveReaderPreviewCache(gid, token, cache)
                        }
                    }
                }
            }
        }
    }

    private val activeScans = ConcurrentHashMap<Long, Job>()

    @Synchronized
    fun launchScan(
        context: Context,
        gid: Long,
        token: String,
        totalPages: Int,
        previewPerPage: Int = 40,
        bypassPages: Set<Int> = emptySet(),
    ): Job {
        val existing = activeScans[gid]
        if (existing?.isActive == true) {
            return existing
        }
        val appContext = context.applicationContext
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                scanGalleryPreviews(
                    appContext,
                    gid,
                    token,
                    totalPages,
                    previewPerPage,
                    bypassPages,
                )
            } finally {
                synchronized(ThumbnailAdScanner) {
                    activeScans.remove(gid)
                }
            }
        }
        activeScans[gid] = job
        return job
    }

    suspend fun scanGalleryPreviews(
        context: Context,
        gid: Long,
        token: String,
        totalPages: Int,
        previewPerPage: Int = 40,
        bypassPages: Set<Int> = emptySet(),
    ) = withContext(Dispatchers.IO) {
        if (!Settings.stripExtraneousAds || totalPages <= 0) return@withContext
        val startIndex = maxOf(0, totalPages - 10)
        val cachedSpiderInfo = readFromCache(gid)
        val alreadyBlocked = cachedSpiderInfo?.blockedAdPages ?: emptySet()
        val targetIndices = (startIndex until totalPages).filter {
            it !in alreadyBlocked && !bypassPages.contains(it)
        }.reversed()
        Log.d("AdBlockDebug", "scanGalleryPreviews: gid=$gid, targetIndices=$targetIndices, totalPages=$totalPages")
        if (targetIndices.isEmpty()) return@withContext

        val cachedPreviews = if (token.isNotEmpty()) loadReaderPreviewCache(gid, token) else null
        val previewMap = cachedPreviews?.toMutableMap() ?: mutableMapOf()

        val missingIndices = targetIndices.filter { it !in previewMap }
        if (missingIndices.isNotEmpty() && token.isNotEmpty()) {
            var ppp = previewPerPage.takeIf { it > 0 } ?: 40
            if (previewMap.isNotEmpty()) {
                val page0Max = previewMap.keys.filter { it < 50 }.maxOrNull()
                if (page0Max != null && page0Max in 19..20) {
                    ppp = 20
                }
            }
            val neededPreviewPages = missingIndices.map { it / ppp }.distinct().toMutableList()
            var i = 0
            while (i < neededPreviewPages.size) {
                val previewPage = neededPreviewPages[i++]
                runSuspendCatching {
                    val previewSet = EhEngine.getPreviewSet(
                        EhUrl.getGalleryDetailUrl(gid, token, previewPage, false),
                    ).first
                    val actualSize = previewSet.size()
                    if (actualSize > 0 && actualSize != ppp) {
                        ppp = actualSize
                        val remainingMissing = targetIndices.filter { it !in previewMap }
                        val newNeeded = remainingMissing.map { it / ppp }.distinct().filter { it !in neededPreviewPages }
                        neededPreviewPages.addAll(newNeeded)
                    }
                    for (j in 0 until previewSet.size()) {
                        val p = previewSet.getGalleryPreview(gid, j)
                        previewMap[p.position] = p
                    }
                }.onFailure { it.printStackTrace() }
            }
            saveReaderPreviewCache(gid, token, previewMap)
        }

        for (index in targetIndices) {
            currentCoroutineContext().ensureActive()
            if (bypassPages.contains(index)) continue
            val preview = previewMap[index]
            if (preview == null) {
                Log.d("AdBlockDebug", "scanGalleryPreviews: preview is null for page $index")
                continue
            }
            val isAd = runSuspendCatching {
                scanPreview(context, preview)
            }.onFailure {
                Log.e("AdBlockDebug", "scanPreview failed for page $index", it)
            }.getOrDefault(false)

            Log.d("AdBlockDebug", "scanGalleryPreviews: page $index isAd=$isAd")
            if (isAd) {
                preview.isAdBlocked = true
                previewMap[index] = preview
                val info = readFromCache(gid) ?: SpiderInfo(gid, token, totalPages).apply {
                    hasAds = true
                }
                info.blockedAdPages.add(index)
                info.saveToCache()
                if (token.isNotEmpty()) {
                    saveReaderPreviewCache(gid, token, previewMap)
                }
                notifyAdBlocked(gid, index, true, token)
                Log.d("AdBlockDebug", "scanGalleryPreviews: ad detected for page $index, notified")
            }
        }
        // Pass 2: Sandwich inference for tail ad clusters
        val blockedNow = (readFromCache(gid)?.blockedAdPages ?: emptySet()).toMutableSet()
        if (blockedNow.isNotEmpty()) {
            val startIndex = maxOf(0, totalPages - 10)
            for (index in (startIndex + 1) until (totalPages - 1)) {
                if (index !in blockedNow && !bypassPages.contains(index)) {
                    val isSandwiched = (index - 1) in blockedNow && (index + 1) in blockedNow
                    if (isSandwiched) {
                        Log.d("AdBlockDebug", "scanGalleryPreviews: page $index is sandwiched between ads, marking as ad")
                        val preview = previewMap[index]
                        if (preview != null) {
                            preview.isAdBlocked = true
                            previewMap[index] = preview
                        }
                        blockedNow.add(index)
                        val info = readFromCache(gid) ?: SpiderInfo(gid, token, totalPages).apply {
                            hasAds = true
                        }
                        info.blockedAdPages.add(index)
                        info.saveToCache()
                        notifyAdBlocked(gid, index, true, token)
                    }
                }
            }
        }

        // Pass 3: Deep check candidates preceding tail ad clusters

        val blockedAfterPass2 = (readFromCache(gid)?.blockedAdPages ?: emptySet()).toMutableSet()
        if (blockedAfterPass2.isNotEmpty() && token.isNotEmpty()) {
            val minBlocked = blockedAfterPass2.filter { it >= maxOf(0, totalPages - 10) }.minOrNull()
            if (minBlocked != null && minBlocked > 0) {
                val candidates = ((minBlocked - 2).coerceAtLeast(0) until minBlocked)
                    .filter { it !in blockedAfterPass2 && !bypassPages.contains(it) }
                    .reversed()
                for (candIndex in candidates) {
                    currentCoroutineContext().ensureActive()
                    val p = previewMap[candIndex] ?: continue
                    val isFullAd = verifyWithFullImage(context, gid, token, p)
                    if (isFullAd) {
                        Log.d("AdBlockDebug", "verifyWithFullImage: candidate page $candIndex confirmed as ad!")
                        p.isAdBlocked = true
                        previewMap[candIndex] = p
                        blockedAfterPass2.add(candIndex)
                        val info = readFromCache(gid) ?: SpiderInfo(gid, token, totalPages).apply {
                            hasAds = true
                        }
                        info.blockedAdPages.add(candIndex)
                        info.saveToCache()
                        notifyAdBlocked(gid, candIndex, true, token)
                    } else {
                        break
                    }
                }
            }
        }

        if (token.isNotEmpty()) {
            saveReaderPreviewCache(gid, token, previewMap)
        }
    }

    internal fun canClip(preview: GalleryPreview, bitmapWidth: Int, bitmapHeight: Int): Boolean = preview.hasClipAspect() &&
        preview.offsetX >= 0 && preview.offsetY >= 0 &&
        preview.offsetX + preview.clipWidth <= bitmapWidth &&
        preview.offsetY + preview.clipHeight <= bitmapHeight

    suspend fun scanPreview(context: Context, preview: GalleryPreview): Boolean = withContext(Dispatchers.IO) {
        val url = preview.imageUrl?.takeIf { it.isNotBlank() } ?: run {
            Log.d("AdBlockDebug", "scanPreview: preview ${preview.position} has no imageUrl")
            return@withContext false
        }
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false) // Required for JNI AndroidBitmap_lockPixels
            .size(SizeResolver.ORIGINAL)
            .apply {
                preview.imageKey?.takeIf { it.isNotBlank() }?.let { key ->
                    memoryCacheKey(key)
                    diskCacheKey(key)
                }
            }
            .build()
        val result = context.imageLoader.execute(request)
        val image = result.image ?: run {
            Log.d("AdBlockDebug", "scanPreview: execute returned null image for url=$url")
            return@withContext false
        }
        val originalBitmap = (image as? BitmapImageWithExtraInfo)?.image?.bitmap
            ?: (image as? BitmapImage)?.bitmap
            ?: runCatching { image.toBitmap() }.getOrNull()
            ?: run {
                Log.d("AdBlockDebug", "scanPreview: failed to extract bitmap from image $image")
                return@withContext false
            }

        if (originalBitmap.isRecycled) return@withContext false

        val canClip = canClip(preview, originalBitmap.width, originalBitmap.height)
        val targetBitmap = if (canClip) {
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
            val isHashBlocked = hash != 0L && AdBlockManager.isBlocked(hash)
            val hasQr = if (isHashBlocked) false else hasQrCode(targetBitmap)
            Log.d("AdBlockDebug", "scanPreview page ${preview.position}: size=${targetBitmap.width}x${targetBitmap.height}, canClip=$canClip, hash=$hash, isHashBlocked=$isHashBlocked, hasQr=$hasQr")
            if (isHashBlocked) {
                return@withContext true
            }

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

    private suspend fun verifyWithFullImage(
        context: Context,
        gid: Long,
        token: String,
        preview: GalleryPreview,
    ): Boolean = withContext(Dispatchers.IO) {
        val pageUrl = preview.pageUrl?.takeIf { it.isNotBlank() } ?: return@withContext false
        val pageResult = runCatching {
            EhEngine.getGalleryPage(pageUrl, gid, token)
        }.getOrNull() ?: return@withContext false
        val targetUrl = pageResult.imageUrl.takeIf { it.isNotBlank() } ?: return@withContext false

        val request = ImageRequest.Builder(context)
            .data(targetUrl)
            .allowHardware(false)
            .size(SizeResolver.ORIGINAL)
            .build()
        val result = context.imageLoader.execute(request)
        val image = result.image ?: return@withContext false
        val bitmap = (image as? BitmapImageWithExtraInfo)?.image?.bitmap
            ?: (image as? BitmapImage)?.bitmap
            ?: runCatching { image.toBitmap() }.getOrNull()
            ?: return@withContext false

        if (bitmap.isRecycled) return@withContext false
        val hash = getDHash(bitmap)
        if (hash != 0L && AdBlockManager.isBlocked(hash)) {
            return@withContext true
        }
        val hasQr = hasQrCode(bitmap)
        if (hasQr) {
            if (hash != 0L) {
                AdBlockManager.addHash(hash)
            }
            return@withContext true
        }
        false
    }
}
