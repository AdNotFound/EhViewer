package com.hippo.ehviewer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSidebarLayoutUtilsTest {
    @Test
    fun portraitWideAndPairedPagesHaveTheSameHeight() {
        val portrait = ReaderSidebarLayoutUtils.calculate(284, 4, true, 0.7f)
        val wide = ReaderSidebarLayoutUtils.calculate(284, 4, true, 1.4f)
        val pair = ReaderSidebarLayoutUtils.calculate(284, 4, true, 0.7f, 0.7f)
        assertEquals(200, portrait.height)
        assertEquals(portrait.height, wide.height)
        assertEquals(portrait.height, pair.height)
        assertEquals(140, portrait.primaryWidth)
        assertEquals(280, wide.primaryWidth)
        assertEquals(140, pair.primaryWidth)
        assertEquals(140, pair.secondaryWidth)
    }

    @Test
    fun resizingAndAsynchronousAspectUpdatesPreserveAlignment() {
        for (width in listOf(0, 3, 57, 285, 640)) {
            val placeholder = ReaderSidebarLayoutUtils.calculate(width, 4, true, Float.NaN)
            for (aspect in listOf(0.5f, 0.7f, 1.4f, 2f)) {
                val single = ReaderSidebarLayoutUtils.calculate(width, 4, true, aspect)
                val pair = ReaderSidebarLayoutUtils.calculate(width, 4, true, aspect, 0.8f)
                assertEquals(placeholder.height, single.height)
                assertEquals(placeholder.height, pair.height)
                assertEquals(width, single.leadingSpace + single.primaryWidth + single.trailingSpace)
                assertTrue(kotlin.math.abs(single.leadingSpace - single.trailingSpace) <= 1)
                assertEquals(width, pair.primaryWidth + pair.secondaryWidth + minOf(4, width))
            }
        }
    }

    @Test
    fun singlePageModeAlsoUsesOneHeightAndLimitsVeryWidePages() {
        val portrait = ReaderSidebarLayoutUtils.calculate(140, 4, false, 0.7f)
        val wide = ReaderSidebarLayoutUtils.calculate(140, 4, false, 2f)
        assertEquals(200, portrait.height)
        assertEquals(portrait.height, wide.height)
        assertEquals(140, portrait.primaryWidth)
        assertEquals(140, wide.primaryWidth)
    }
}
