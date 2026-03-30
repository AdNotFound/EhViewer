package com.hippo.ehviewer.client.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryDetailUrlParserTest {
    @Test
    fun parseStrictGalleryUrlReturnsResult() {
        val result = GalleryDetailUrlParser.parse("https://exhentai.org/g/1234567/abcdef1234/")

        assertNotNull(result)
        assertEquals(1234567L, result!!.gid)
        assertEquals("abcdef1234", result.token)
    }

    @Test
    fun parseInvalidTokenReturnsNull() {
        val result = GalleryDetailUrlParser.parse("https://exhentai.org/g/1234567/not-token/")

        assertNull(result)
    }
}
