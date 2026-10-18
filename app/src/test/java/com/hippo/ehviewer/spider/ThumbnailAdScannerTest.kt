package com.hippo.ehviewer.spider

import com.hippo.ehviewer.client.data.GalleryPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailAdScannerTest {
    @Test
    fun testClipBoundsValidation() {
        val normalPreview = GalleryPreview(
            position = 9,
            imageUrl = "https://ehgt.org/t/sprite.jpg",
            offsetX = 100,
            offsetY = 0,
            clipWidth = 100,
            clipHeight = 140,
        )
        assertTrue(normalPreview.hasClipAspect())
        assertEquals(100, normalPreview.clipWidth)
        assertEquals(140, normalPreview.clipHeight)

        val largePreview = GalleryPreview(
            position = 9,
            imageUrl = "https://ehgt.org/t/large.jpg",
            previewWidth = 250,
            previewHeight = 350,
        )
        assertFalse(largePreview.hasClipAspect())
        assertTrue(largePreview.hasPreviewAspect())
    }

    @Test
    fun testCanClipValidation() {
        val validPreview = GalleryPreview(
            imageUrl = "https://ehgt.org/t/sprite.jpg",
            offsetX = 100,
            offsetY = 50,
            clipWidth = 100,
            clipHeight = 140,
        )
        assertTrue(ThumbnailAdScanner.canClip(validPreview, 300, 300))

        // Negative offset X
        val negXPreview = GalleryPreview(
            imageUrl = "https://ehgt.org/t/sprite.jpg",
            offsetX = -1,
            offsetY = 0,
            clipWidth = 100,
            clipHeight = 140,
        )
        assertFalse(ThumbnailAdScanner.canClip(negXPreview, 300, 300))

        // Negative offset Y
        val negYPreview = GalleryPreview(
            imageUrl = "https://ehgt.org/t/sprite.jpg",
            offsetX = 0,
            offsetY = -1,
            clipWidth = 100,
            clipHeight = 140,
        )
        assertFalse(ThumbnailAdScanner.canClip(negYPreview, 300, 300))

        // Out of bounds width
        val outOfBoundsWidthPreview = GalleryPreview(
            imageUrl = "https://ehgt.org/t/sprite.jpg",
            offsetX = 250,
            offsetY = 0,
            clipWidth = 100,
            clipHeight = 140,
        )
        assertFalse(ThumbnailAdScanner.canClip(outOfBoundsWidthPreview, 300, 300))

        // Out of bounds height
        val outOfBoundsHeightPreview = GalleryPreview(
            imageUrl = "https://ehgt.org/t/sprite.jpg",
            offsetX = 0,
            offsetY = 200,
            clipWidth = 100,
            clipHeight = 140,
        )
        assertFalse(ThumbnailAdScanner.canClip(outOfBoundsHeightPreview, 300, 300))

        // Exact edge boundary (touching right and bottom edges)
        val edgePreview = GalleryPreview(
            imageUrl = "https://ehgt.org/t/sprite.jpg",
            offsetX = 200,
            offsetY = 160,
            clipWidth = 100,
            clipHeight = 140,
        )
        assertTrue(ThumbnailAdScanner.canClip(edgePreview, 300, 300))

        // Non-positive clip width or height
        val zeroWidthPreview = GalleryPreview(
            imageUrl = "https://ehgt.org/t/sprite.jpg",
            offsetX = 0,
            offsetY = 0,
            clipWidth = 0,
            clipHeight = 140,
        )
        assertFalse(ThumbnailAdScanner.canClip(zeroWidthPreview, 300, 300))

        val negHeightPreview = GalleryPreview(
            imageUrl = "https://ehgt.org/t/sprite.jpg",
            offsetX = 0,
            offsetY = 0,
            clipWidth = 100,
            clipHeight = -10,
        )
        assertFalse(ThumbnailAdScanner.canClip(negHeightPreview, 300, 300))

        // Large preview without clip aspect
        val largePreview = GalleryPreview(
            imageUrl = "https://ehgt.org/t/large.jpg",
            previewWidth = 250,
            previewHeight = 350,
        )
        assertFalse(ThumbnailAdScanner.canClip(largePreview, 250, 350))
    }
}
