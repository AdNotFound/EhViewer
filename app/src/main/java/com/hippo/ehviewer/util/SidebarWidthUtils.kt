package com.hippo.ehviewer.util

import kotlin.math.roundToInt

object SidebarWidthUtils {
    const val DETAIL_MIN_PERCENT = 25
    const val DETAIL_MAX_PERCENT = 60
    const val READER_MIN_PERCENT = 3
    const val READER_MAX_PERCENT = 35

    fun migrateDetailWidth(value: Int): Int = intArrayOf(25, 31, 37, 43, 49, 55, 60)[value.coerceIn(0, 6)] - DETAIL_MIN_PERCENT

    fun paneWidth(availableWidth: Int, percent: Int, minPaneWidth: Int, minContentWidth: Int, dividerWidth: Int): Int {
        val maxWidth = (availableWidth - minContentWidth - dividerWidth).coerceAtLeast(0)
        return (availableWidth * percent / 100f).roundToInt().coerceIn(minPaneWidth.coerceAtMost(maxWidth), maxWidth)
    }

    fun draggedPercent(startWidth: Int, deltaX: Float, availableWidth: Int, direction: Int, range: IntRange): Int {
        if (availableWidth <= 0) return range.first
        return ((startWidth + deltaX * direction) * 100 / availableWidth).roundToInt().coerceIn(range)
    }
}
