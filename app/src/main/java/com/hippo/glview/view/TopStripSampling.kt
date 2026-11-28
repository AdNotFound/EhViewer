package com.hippo.glview.view

import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Pure sampling geometry, luminance averaging, and hysteresis helpers for
 * [GLRootView]'s HUD foreground adaptation. Kept free of Android framework
 * dependencies so it can be unit-tested directly on the JVM.
 */
object TopStripSampling {
    const val LIGHT_INITIAL_THRESHOLD = 0.55f
    const val LIGHT_ENTER_THRESHOLD = 0.60f
    const val DARK_ENTER_THRESHOLD = 0.50f
    const val SAMPLE_STRIDE_X = 6
    const val SAMPLE_STRIDE_Y = 3

    data class SampleRegion(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val isEmpty: Boolean get() = right <= left || bottom <= top
    }

    /**
     * Clamps [region] to `[0, surfaceWidth] x [0, surfaceHeight]`, returning
     * `null` if the resulting area is empty.
     */
    @JvmStatic
    fun clampRegion(
        region: SampleRegion?,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ): SampleRegion? {
        if (region == null || surfaceWidth <= 0 || surfaceHeight <= 0) return null
        val left = max(0, region.left)
        val top = max(0, region.top)
        val right = min(surfaceWidth, region.right)
        val bottom = min(surfaceHeight, region.bottom)
        if (right <= left || bottom <= top) return null
        return SampleRegion(left, top, right, bottom)
    }

    /**
     * Computes the tight bounding box enclosing all non-empty clamped [regions],
     * or `null` when nothing needs to be sampled.
     */
    @JvmStatic
    fun computeUnionBounds(
        regions: Array<SampleRegion?>,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ): SampleRegion? {
        var unionLeft = Int.MAX_VALUE
        var unionTop = Int.MAX_VALUE
        var unionRight = Int.MIN_VALUE
        var unionBottom = Int.MIN_VALUE
        var any = false
        for (region in regions) {
            val clamped = clampRegion(region, surfaceWidth, surfaceHeight) ?: continue
            unionLeft = min(unionLeft, clamped.left)
            unionTop = min(unionTop, clamped.top)
            unionRight = max(unionRight, clamped.right)
            unionBottom = max(unionBottom, clamped.bottom)
            any = true
        }
        return if (any) SampleRegion(unionLeft, unionTop, unionRight, unionBottom) else null
    }

    /**
     * Computes the mean luma (`0f..1f`) for [clampedRegion] from an RGBA
     * [ByteBuffer] captured via `glReadPixels` over [union] (where row 0 is the
     * bottom of [union] in View coordinates).
     */
    @JvmStatic
    fun computeRegionLuminance(
        rgba: ByteBuffer,
        union: SampleRegion,
        clampedRegion: SampleRegion,
    ): Float? {
        if (union.isEmpty || clampedRegion.isEmpty) return null
        val unionWidth = union.width
        val bufXStart = max(0, clampedRegion.left - union.left)
        val bufXEnd = min(unionWidth, clampedRegion.right - union.left)
        val bufYStart = max(0, union.bottom - clampedRegion.bottom)
        val bufYEnd = min(union.height, union.bottom - clampedRegion.top)
        if (bufXEnd <= bufXStart || bufYEnd <= bufYStart) return null

        var sum = 0L
        var count = 0
        var y = bufYStart
        while (y < bufYEnd) {
            val rowOffset = y * unionWidth * 4
            var x = bufXStart
            while (x < bufXEnd) {
                val i = rowOffset + x * 4
                val r = rgba.get(i).toInt() and 0xff
                val g = rgba.get(i + 1).toInt() and 0xff
                val b = rgba.get(i + 2).toInt() and 0xff
                sum += (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt()
                count++
                x += SAMPLE_STRIDE_X
            }
            y += SAMPLE_STRIDE_Y
        }
        if (count == 0) return null
        return sum / count.toFloat() / 255f
    }

    /**
     * Applies hysteresis to convert [luminance] into a light/dark verdict.
     */
    @JvmStatic
    fun resolveLightState(luminance: Float, previous: Boolean?): Boolean = when (previous) {
        null -> luminance > LIGHT_INITIAL_THRESHOLD
        true -> luminance >= DARK_ENTER_THRESHOLD
        false -> luminance > LIGHT_ENTER_THRESHOLD
    }
}
