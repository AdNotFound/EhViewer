package com.hippo.ehviewer.adblock

import android.content.Context
import android.content.ContextWrapper
import com.hippo.ehviewer.AppConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AdBlockManagerTest {
    private class FakeContext(private val targetDir: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = targetDir
    }

    companion object {
        private lateinit var tempDir: File

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            tempDir = Files.createTempDirectory("adblock_unit_test").toFile()
            AppConfig.initialize(FakeContext(tempDir))
        }
    }

    @Before
    fun setUp() {
        AdBlockManager.clear()
    }

    @After
    fun tearDown() {
        AdBlockManager.clear()
    }

    @Test
    fun testEmptyAndClear() {
        assertTrue(AdBlockManager.isEmpty())
        val hash = 0x123456789ABCDEF0L
        AdBlockManager.addHash(hash)
        assertFalse(AdBlockManager.isEmpty())
        AdBlockManager.clear()
        assertTrue(AdBlockManager.isEmpty())
    }

    @Test
    fun testIgnoreZeroHash() {
        AdBlockManager.addHash(0L)
        assertTrue(AdBlockManager.isEmpty())
        assertFalse(AdBlockManager.isBlocked(0L))
        AdBlockManager.unblock(0L)
        assertTrue(AdBlockManager.isEmpty())
    }

    @Test
    fun testExactMatch() {
        val hash = 0x21B2C3D4E5F60718L
        AdBlockManager.addHash(hash)
        assertTrue(AdBlockManager.isBlocked(hash))
        assertFalse(AdBlockManager.isBlocked(0x1122334455667788L))
    }

    @Test
    fun testHammingDistance1And2() {
        val base = 0x1111222233334444L
        AdBlockManager.addHash(base)

        // Hamming distance 1: flipped bit 4
        val dist1 = base xor (1L shl 4)
        assertTrue(AdBlockManager.isBlocked(dist1))

        // Hamming distance 2 within the same 16-bit chunk
        val dist2SameChunk = base xor (3L shl 2)
        assertTrue(AdBlockManager.isBlocked(dist2SameChunk))

        // Hamming distance 2 across different 16-bit chunks (bit 2 in chunk 0 and bit 20 in chunk 1)
        val dist2DiffChunk = base xor (1L shl 2) xor (1L shl 20)
        assertTrue(AdBlockManager.isBlocked(dist2DiffChunk))
    }

    @Test
    fun testHammingDistance3Rejected() {
        val base = 0x1111222233334444L
        AdBlockManager.addHash(base)

        // Hamming distance 3: flipped 3 bits
        val dist3 = base xor 0b111L
        assertFalse(AdBlockManager.isBlocked(dist3))

        // Hamming distance 4: flipped 4 bits across chunks
        val dist4 = base xor (1L shl 1) xor (1L shl 17) xor (1L shl 33) xor (1L shl 49)
        assertFalse(AdBlockManager.isBlocked(dist4))
    }

    @Test
    fun testUnblock() {
        val base = 0x5555AAAA5555AAAAL
        AdBlockManager.addHash(base)
        assertTrue(AdBlockManager.isBlocked(base))

        // Unblock with a hash at distance 1 removes the matching candidate
        val near = base xor (1L shl 10)
        AdBlockManager.unblock(near)
        assertFalse(AdBlockManager.isBlocked(base))
        assertTrue(AdBlockManager.isEmpty())
    }

    @Test
    fun testRemoveHash() {
        val hash1 = 0x1111111111111111L
        val hash2 = 0x2222222222222222L
        AdBlockManager.addHash(hash1)
        AdBlockManager.addHash(hash2)

        AdBlockManager.removeHash(hash1)
        assertFalse(AdBlockManager.isBlocked(hash1))
        assertTrue(AdBlockManager.isBlocked(hash2))
    }
}
