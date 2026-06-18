package com.hippo.ehviewer.coil

import coil3.disk.DiskCache
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.client.data.GalleryPreview
import com.hippo.util.runSuspendCatching
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

@Serializable
data class ReaderPreviewCache(
    val gid: Long,
    val token: String,
    val timestamp: Long,
    val previews: List<CachedPreviewItem>,
)

@Serializable
data class CachedPreviewItem(
    val position: Int,
    val imageKey: String,
    val imageUrl: String,
    val pageUrl: String,
    val previewWidth: Int,
    val previewHeight: Int,
    val offsetX: Int,
    val offsetY: Int,
    val clipWidth: Int,
    val clipHeight: Int,
)

private val cbor = Cbor { ignoreUnknownKeys = true }

private val readerPreviewCache by lazy {
    DiskCache.Builder()
        .directory(EhApplication.cacheDir / "reader_preview_cache")
        .maxSizeBytes(10 * 1024 * 1024)
        .build()
}

private fun CachedPreviewItem.toGalleryPreview() = GalleryPreview(
    imageKey = imageKey,
    imageUrl = imageUrl,
    pageUrl = pageUrl,
    position = position,
    previewWidth = previewWidth,
    previewHeight = previewHeight,
    offsetX = offsetX,
    offsetY = offsetY,
    clipWidth = clipWidth,
    clipHeight = clipHeight,
)

private val CACHE_TTL_MS = 48 * 60 * 60 * 1000L

fun loadReaderPreviewCache(gid: Long, token: String): Map<Int, GalleryPreview>? = runCatching {
    readerPreviewCache.openSnapshot(gid.toString())?.use { snapshot ->
        val cache = cbor.decodeFromByteArray<ReaderPreviewCache>(snapshot.data.toFile().readBytes())
        if (cache.token != token) return@use null
        if (System.currentTimeMillis() - cache.timestamp > CACHE_TTL_MS) return@use null
        cache.previews.associate { it.position to it.toGalleryPreview() }
    }
}.onFailure {
    it.printStackTrace()
}.getOrNull()

fun saveReaderPreviewCache(gid: Long, token: String, map: Map<Int, GalleryPreview>) = runSuspendCatching {
    val cache = ReaderPreviewCache(
        gid = gid,
        token = token,
        timestamp = System.currentTimeMillis(),
        previews = map.entries.map { (_, preview) ->
            CachedPreviewItem(
                position = preview.position,
                imageKey = preview.imageKey ?: "",
                imageUrl = preview.imageUrl ?: "",
                pageUrl = preview.pageUrl ?: "",
                previewWidth = preview.previewWidth,
                previewHeight = preview.previewHeight,
                offsetX = preview.offsetX,
                offsetY = preview.offsetY,
                clipWidth = preview.clipWidth,
                clipHeight = preview.clipHeight,
            )
        },
    )
    readerPreviewCache.edit(gid.toString()) {
        data.toFile().writeBytes(cbor.encodeToByteArray(cache))
    }
}.onFailure {
    it.printStackTrace()
}
