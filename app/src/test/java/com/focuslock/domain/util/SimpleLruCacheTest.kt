package com.focuslock.domain.util

import com.focuslock.service.vpn.VpnPacketLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bounded cache four features depend on (URL verdicts, page signals, smart scans, and now the
 * VPN DNS response cache). The VPN cache's entry bound is this container's `maxSize`, so the bound
 * is only as good as this class.
 */
class SimpleLruCacheTest {

    @Test
    fun `putting beyond the cap evicts the least recently used entry`() {
        val cache = SimpleLruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        cache.put("d", 4)

        assertNull("the oldest entry must be evicted", cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertEquals(3, cache.get("c"))
        assertEquals(4, cache.get("d"))
    }

    @Test
    fun `get refreshes recency so a read entry survives the next eviction`() {
        val cache = SimpleLruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        assertEquals(1, cache.get("a")) // "a" is now the most recently used
        cache.put("d", 4)

        assertNull("the least recently used entry goes, not the last read", cache.get("b"))
        assertEquals(1, cache.get("a"))
    }

    @Test
    fun `re-putting a key updates it without growing the cache`() {
        val cache = SimpleLruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)

        cache.put("a", 9)

        assertEquals(9, cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertTrue(cache.containsKey("a"))
    }

    @Test
    fun `remove and clear drop entries`() {
        val cache = SimpleLruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)

        cache.remove("a")
        assertFalse(cache.containsKey("a"))
        assertEquals(2, cache.get("b"))

        cache.clear()
        assertNull(cache.get("b"))
    }

    @Test
    fun `the vpn bound admits at most MAX_CACHE_ENTRIES entries`() {
        // Ties the test to the production bound rather than a local one: if the VPN cache ever
        // grows past this, the container it is built on is what fails.
        val cap = VpnPacketLoop.MAX_CACHE_ENTRIES
        val cache = SimpleLruCache<Int, Int>(cap)
        repeat(cap + 50) { key -> cache.put(key, key) }

        val retained = (0 until cap + 50).count { cache.get(it) != null }
        assertEquals("the cache must hold no more than its bound", cap, retained)
        assertNull("the oldest entries are the ones evicted", cache.get(0))
        assertEquals(cap + 49, cache.get(cap + 49))
    }
}
