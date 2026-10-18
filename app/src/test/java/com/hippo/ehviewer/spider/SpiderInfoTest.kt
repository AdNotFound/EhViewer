package com.hippo.ehviewer.spider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiderInfoTest {
    @Test
    fun testBlockedAdPagesSerialization() {
        val info = SpiderInfo(
            gid = 12345L,
            token = "abcdef",
        ).apply {
            blockedAdPages.add(18)
            blockedAdPages.add(19)
            bypassedAdPages.add(17)
            hasAds = true
        }

        val bytes = SpiderInfo.encode(info)
        val decoded = SpiderInfo.decode(bytes)

        assertEquals(12345L, decoded.gid)
        assertEquals("abcdef", decoded.token)
        assertTrue(decoded.blockedAdPages.contains(18))
        assertTrue(decoded.blockedAdPages.contains(19))
        assertTrue(decoded.bypassedAdPages.contains(17))
        assertTrue(decoded.hasAds)
    }
}
