package com.hippo.glview.view

import com.hippo.glview.view.TopStripSampling.SampleRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TopStripSamplingTest {

    @Test
    fun clampRegionAndComputeUnionBounds() {
        val regions = arrayOf(
            SampleRegion(-10, 20, 40, 60),
            SampleRegion(0, 0, 0, 0),
            SampleRegion(900, 30, 1100, 70),
            null,
        )
        val union = TopStripSampling.computeUnionBounds(regions, 1000, 2000)
        assertEquals(SampleRegion(0, 20, 1000, 70), union)
    }

    @Test
    fun computeUnionBoundsReturnsNullWhenNothingVisible() {
        val regions = arrayOf<SampleRegion?>(
            null,
            SampleRegion(10, 10, 10, 30),
            SampleRegion(1050, 10, 1100, 30),
        )
        assertNull(TopStripSampling.computeUnionBounds(regions, 1000, 2000))
    }

    @Test
    fun computeRegionLuminanceAccountsForBottomLeftGlOrigin() {
        // Union spans View x=[10, 30), y=[20, 40) -> width=20, height=20.
        // GL row 0 corresponds to View y=39 (bottom), GL row 19 to View y=20 (top).
        val union = SampleRegion(10, 20, 30, 40)
        val buffer = ByteBuffer.allocateDirect(union.width * union.height * 4)
            .order(ByteOrder.nativeOrder())

        // Fill top half of View (View y in [20, 30) -> GL rows [10, 20)) with white,
        // and bottom half (View y in [30, 40) -> GL rows [0, 10)) with black.
        for (glY in 0 until union.height) {
            val v = if (glY >= 10) 255.toByte() else 0.toByte()
            for (x in 0 until union.width) {
                val i = (glY * union.width + x) * 4
                buffer.put(i, v)
                buffer.put(i + 1, v)
                buffer.put(i + 2, v)
                buffer.put(i + 3, 255.toByte())
            }
        }

        val topLum = TopStripSampling.computeRegionLuminance(
            buffer,
            union,
            SampleRegion(10, 20, 30, 30),
        )!!
        val bottomLum = TopStripSampling.computeRegionLuminance(
            buffer,
            union,
            SampleRegion(10, 30, 30, 40),
        )!!

        assertEquals(1.0f, topLum, 0.01f)
        assertEquals(0.0f, bottomLum, 0.01f)
    }

    @Test
    fun computeRegionLuminanceSupportsSplitLeftAndRightRegions() {
        val union = SampleRegion(0, 0, 20, 10)
        val buffer = ByteBuffer.allocateDirect(union.width * union.height * 4)
            .order(ByteOrder.nativeOrder())

        for (glY in 0 until union.height) {
            for (x in 0 until union.width) {
                val v = if (x < 10) 0.toByte() else 255.toByte()
                val i = (glY * union.width + x) * 4
                buffer.put(i, v)
                buffer.put(i + 1, v)
                buffer.put(i + 2, v)
                buffer.put(i + 3, 255.toByte())
            }
        }

        val leftLum = TopStripSampling.computeRegionLuminance(buffer, union, SampleRegion(0, 0, 8, 10))!!
        val rightLum = TopStripSampling.computeRegionLuminance(buffer, union, SampleRegion(12, 0, 20, 10))!!

        assertEquals(0.0f, leftLum, 0.01f)
        assertEquals(1.0f, rightLum, 0.01f)
    }

    @Test
    fun resolveLightStateAppliesHysteresis() {
        assertFalse(TopStripSampling.resolveLightState(0.55f, null))
        assertTrue(TopStripSampling.resolveLightState(0.56f, null))

        // Currently light: stays light down to 0.50, flips dark below 0.50.
        assertTrue(TopStripSampling.resolveLightState(0.50f, true))
        assertFalse(TopStripSampling.resolveLightState(0.49f, true))

        // Currently dark: stays dark up to 0.60, flips light above 0.60.
        assertFalse(TopStripSampling.resolveLightState(0.60f, false))
        assertTrue(TopStripSampling.resolveLightState(0.61f, false))
    }
}
