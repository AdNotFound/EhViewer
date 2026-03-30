package com.hippo.ehviewer.client.parser

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryNotAvailableParserTest {
    @Test
    fun parseCopyrightMessageReturnsReadableText() {
        val html =
            """
            <html>
              <body>
                <div class="d">
                  <p>This gallery is unavailable due to a copyright claim.<br>Contact us for more information.</p>
                </div>
              </body>
            </html>
            """.trimIndent()

        val result = GalleryNotAvailableParser.parse(html)

        assertNotNull(result)
        assertTrue(result!!.contains("unavailable due to a copyright claim"))
    }
}
