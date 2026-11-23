package com.hippo.ehviewer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarWidthUtilsTest {
    @Test
    fun migrationPreservesAllSevenExistingPercentages() {
        val percentages = intArrayOf(25, 31, 37, 43, 49, 55, 60)
        percentages.forEachIndexed { index, percent ->
            assertEquals(percent, SidebarWidthUtils.migrateDetailWidth(index) + SidebarWidthUtils.DETAIL_MIN_PERCENT)
        }
        assertEquals(0, SidebarWidthUtils.migrateDetailWidth(-1))
        assertEquals(35, SidebarWidthUtils.migrateDetailWidth(100))
    }

    @Test
    fun dividerWidthIsReservedWhenMinimumWidthsDoNotFit() {
        assertEquals(276, SidebarWidthUtils.paneWidth(600, 60, 280, 300, 24))
        assertEquals(0, SidebarWidthUtils.paneWidth(200, 60, 280, 300, 24))
        assertEquals(430, SidebarWidthUtils.paneWidth(1000, 43, 280, 300, 24))
        assertEquals(56, SidebarWidthUtils.paneWidth(1000, 3, 56, 160, 24))
    }

    @Test
    fun draggingUsesMeasuredWidthAndSupportsBothSides() {
        val range = 3..35
        assertEquals(20, SidebarWidthUtils.draggedPercent(160, 40f, 1000, 1, range))
        assertEquals(20, SidebarWidthUtils.draggedPercent(160, -40f, 1000, -1, range))
        assertEquals(35, SidebarWidthUtils.draggedPercent(160, 1000f, 1000, 1, range))
        assertEquals(3, SidebarWidthUtils.draggedPercent(160, -1000f, 1000, 1, range))
        assertEquals(3, SidebarWidthUtils.draggedPercent(160, 10f, 0, 1, range))
        // A minimum-width sidebar starts dragging from its visible edge.
        assertEquals(7, SidebarWidthUtils.draggedPercent(56, 10f, 1000, 1, range))
    }
}
