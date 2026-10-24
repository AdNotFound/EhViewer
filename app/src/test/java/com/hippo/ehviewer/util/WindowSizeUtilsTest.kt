package com.hippo.ehviewer.util

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowSizeUtilsTest {

    @Test
    fun testNullConfigurationReturnsFalse() {
        assertFalse(WindowSizeUtils.isLargeScreen(null))
    }

    @Test
    fun testPortraitNeverQualifiesAsLargeScreen() {
        // Large tablet in portrait (e.g. 800dp width)
        val portraitTablet = Configuration().apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = 800
            screenHeightDp = 1200
        }
        assertFalse(WindowSizeUtils.isLargeScreen(portraitTablet))

        // Foldable in portrait (e.g. 680dp width)
        val portraitFoldable = Configuration().apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = 680
            screenHeightDp = 750
        }
        assertFalse(WindowSizeUtils.isLargeScreen(portraitFoldable))

        // Phone in portrait (e.g. 392dp width)
        val portraitPhone = Configuration().apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = 392
            screenHeightDp = 850
        }
        assertFalse(WindowSizeUtils.isLargeScreen(portraitPhone))
    }

    @Test
    fun testLandscapeWithSufficientWidthQualifies() {
        // Tablet in landscape (e.g. 1200dp width)
        val landscapeTablet = Configuration().apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 1200
            screenHeightDp = 800
        }
        assertTrue(WindowSizeUtils.isLargeScreen(landscapeTablet))

        // Foldable in landscape (e.g. 750dp width)
        val landscapeFoldable = Configuration().apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 750
            screenHeightDp = 680
        }
        assertTrue(WindowSizeUtils.isLargeScreen(landscapeFoldable))

        // Boundary test: exactly 600dp in landscape
        val boundaryLandscape = Configuration().apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 600
            screenHeightDp = 400
        }
        assertTrue(WindowSizeUtils.isLargeScreen(boundaryLandscape))
    }

    @Test
    fun testLandscapeNarrowSplitScreenFallsBack() {
        // Narrow split screen / multi-window in landscape (< 600dp width)
        val narrowSplitScreen = Configuration().apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 500
            screenHeightDp = 800
        }
        assertFalse(WindowSizeUtils.isLargeScreen(narrowSplitScreen))
    }

    @Test
    fun testUndefinedOrientationFallsBackToAspectCheck() {
        val undefinedLandscape = Configuration().apply {
            orientation = Configuration.ORIENTATION_UNDEFINED
            screenWidthDp = 800
            screenHeightDp = 600
        }
        assertTrue(WindowSizeUtils.isLargeScreen(undefinedLandscape))

        val undefinedPortrait = Configuration().apply {
            orientation = Configuration.ORIENTATION_UNDEFINED
            screenWidthDp = 600
            screenHeightDp = 800
        }
        assertFalse(WindowSizeUtils.isLargeScreen(undefinedPortrait))
    }
}
