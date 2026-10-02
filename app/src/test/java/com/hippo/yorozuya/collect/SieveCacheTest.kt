package com.hippo.yorozuya.collect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SieveCacheTest {
    @Test
    fun testEvictAllAndReuse() {
        val cache = SieveCache<Int, String>(maxSize = 2)
        cache.put(1, "one")
        cache.put(2, "two")
        assertEquals(2, cache.size())

        cache.evictAll()
        assertEquals(0, cache.size())
        assertNull(cache[1])
        assertNull(cache[2])

        // Add items after evictAll should not corrupt list or hang
        cache.put(3, "three")
        cache.put(4, "four")
        cache.put(5, "five") // Triggers eviction
        assertEquals(2, cache.size())
        assertEquals("five", cache[5])
    }

    @Test
    fun testSingleItemEviction() {
        val cache = SieveCache<Int, String>(maxSize = 1)
        cache.put(1, "one")
        cache.put(2, "two")
        assertEquals(1, cache.size())
        assertNull(cache[1])
        assertEquals("two", cache[2])

        cache.remove(2)
        assertEquals(0, cache.size())

        cache.put(3, "three")
        cache.put(4, "four")
        assertEquals(1, cache.size())
        assertNull(cache[3])
        assertEquals("four", cache[4])
    }
}
