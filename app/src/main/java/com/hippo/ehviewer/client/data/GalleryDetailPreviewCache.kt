package com.hippo.ehviewer.client.data

import com.hippo.ehviewer.EhApplication.Companion.galleryDetailCache

/**
 * Syncs a reusable first preview page into the shared detail cache.
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
    if (cachedPreviewSet == null || cachedPreviewSet.size() <= previewSet.size()) {
        galleryDetail.previewSet = previewSet
    }
    if (previewPages > galleryDetail.previewPages) {
        galleryDetail.previewPages = previewPages
    }
}
