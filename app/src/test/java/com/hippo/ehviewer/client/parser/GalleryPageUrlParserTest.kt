package com.hippo.ehviewer.client.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryPageUrlParserTest {
    @Test
    fun parseStrictGalleryPageUrlReturnsResult() {
        val result = GalleryPageUrlParser.parse("https://e-hentai.org/s/91ea4b6d89/901103-12")

        assertNotNull(result)
        assertEquals(901103L, result!!.gid)
        assertEquals("91ea4b6d89", result.pToken)
        assertEquals(11, result.page)
    }

    @Test
    fun parseWithZeroPageReturnsNull() {
        val result = GalleryPageUrlParser.parse("https://e-hentai.org/s/91ea4b6d89/901103-0")

        assertNull(result)
    }
}
