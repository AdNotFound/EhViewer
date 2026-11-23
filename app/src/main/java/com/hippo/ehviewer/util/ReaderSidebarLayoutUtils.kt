package com.hippo.ehviewer.util

import kotlin.math.roundToInt

internal object ReaderSidebarLayoutUtils {
    const val DEFAULT_ASPECT = 0.7f

    data class Layout(
        val height: Int,
        val primaryWidth: Int,
        val secondaryWidth: Int,
        val leadingSpace: Int,
        val trailingSpace: Int,
    )

    fun calculate(
        width: Int,
        gap: Int,
        isDoublePageMode: Boolean,
        primaryAspect: Float,
        secondaryAspect: Float? = null,
    ): Layout {
        val availableWidth = width.coerceAtLeast(0)
        val gapWidth = gap.coerceIn(0, availableWidth)
        // Reserve the double-page gap even on single-page rows to keep every row equally tall.
        val columns = if (isDoublePageMode) 2 else 1
        val height = ((availableWidth - if (isDoublePageMode) gapWidth else 0) / (DEFAULT_ASPECT * columns)).roundToInt()
        val primary = primaryAspect.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_ASPECT
        if (secondaryAspect != null) {
            val secondary = secondaryAspect.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_ASPECT
            val imageWidth = availableWidth - gapWidth
            val primaryWidth = (imageWidth * primary / (primary + secondary)).roundToInt().coerceIn(0, imageWidth)
            return Layout(height, primaryWidth, imageWidth - primaryWidth, 0, 0)
        }
        val primaryWidth = (height * primary).roundToInt().coerceIn(0, availableWidth)
        val remainingWidth = availableWidth - primaryWidth
        return Layout(height, primaryWidth, 0, remainingWidth / 2, remainingWidth - remainingWidth / 2)
    }
}
