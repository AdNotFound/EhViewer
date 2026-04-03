package com.hippo.ehviewer.ui.scene

import com.hippo.ehviewer.client.data.GalleryCommentList

interface GalleryDetailOverlayHost {
    fun closeDetailOverlay()

    fun onDetailOverlayCommentsUpdated(comments: GalleryCommentList?) {}
}
