package com.hippo.ehviewer.client.data

import com.hippo.ehviewer.EhApplication.Companion.galleryDetailCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private val galleryDetailPreviewUpdateFlow = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)

val galleryDetailPreviewUpdates = galleryDetailPreviewUpdateFlow.asSharedFlow()

/**
 * Syncs a reusable first preview page into the shared detail cache
 * and notifies detail screens to refresh their preview section.
 */
fun cacheGalleryDetailPreviewSet(
    gid: Long,
    previewSet: PreviewSet,
    previewPages: Int = 0,
) {
    if (previewSet.size() <= 0 || previewSet.getPosition(0) != 0) {
        return
    }

    val galleryDetail = galleryDetailCache[gid] ?: return
    val cachedPreviewSet = galleryDetail.previewSet
    var updated = false
    if (cachedPreviewSet == null || cachedPreviewSet.size() <= previewSet.size()) {
        galleryDetail.previewSet = previewSet
        updated = true
    }
    if (previewPages > galleryDetail.previewPages) {
        galleryDetail.previewPages = previewPages
        updated = true
    }
    if (updated) {
        galleryDetailPreviewUpdateFlow.tryEmit(gid)
    }
}
