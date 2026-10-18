package com.hippo.ehviewer.client.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryInfoTest {
    @Test
    fun testIsExtraneousAdsTag() {
        assertTrue(isExtraneousAdsTag("extraneous ads"))
        assertTrue(isExtraneousAdsTag("extraneous_ads"))
        assertTrue(isExtraneousAdsTag("other:extraneous ads"))
        assertTrue(isExtraneousAdsTag("other:extraneous_ads"))
        assertTrue(isExtraneousAdsTag("_Lextraneous ads"))
        assertTrue(isExtraneousAdsTag("_Wextraneous ads"))
        assertTrue(isExtraneousAdsTag("_L_Uextraneous ads"))
        assertTrue(isExtraneousAdsTag("_Dextraneous ads"))
        assertTrue(isExtraneousAdsTag("_Lother:extraneous_ads"))
        assertTrue(isExtraneousAdsTag("other:_Lextraneous ads"))
        assertTrue(isExtraneousAdsTag("other:_Lextraneous_ads"))
        assertTrue(isExtraneousAdsTag("other:_L_Uextraneous ads"))

        assertFalse(isExtraneousAdsTag("no extraneous ads"))
        assertFalse(isExtraneousAdsTag("parody:extraneous ads"))
        assertFalse(isExtraneousAdsTag("female:glasses"))
    }

    @Test
    fun testHasAdsFromSimpleTags() {
        val info = BaseGalleryInfo().apply {
            simpleTags = arrayOf("language:chinese", "other:extraneous_ads")
        }
        assertTrue(info.hasAds)

        val cleanInfo = BaseGalleryInfo().apply {
            simpleTags = arrayOf("language:chinese", "female:glasses")
        }
        assertFalse(cleanInfo.hasAds)
    }
}
