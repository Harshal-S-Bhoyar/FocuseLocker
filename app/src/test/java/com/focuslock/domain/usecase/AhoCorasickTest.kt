package com.focuslock.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AhoCorasickTest {

    @Test
    fun searchFirst_findsSingleKeyword() {
        val aho = AhoCorasick.Builder().addPattern("test").build()
        val match = aho.searchFirst("this is a test string")
        assertNotNull(match)
        assertEquals("test", match?.keyword)
    }

    @Test
    fun searchFirst_findsMultipleKeywords_returnsFirst() {
        val aho = AhoCorasick.Builder().addPattern("test").addPattern("string").build()
        val match = aho.searchFirst("this is a test string")
        // It will find "test" first because it appears earlier in the string
        assertNotNull(match)
        assertEquals("test", match?.keyword)
    }

    @Test
    fun searchFirst_findsOverlappingKeywords() {
        val aho = AhoCorasick.Builder()
            .addPattern("he")
            .addPattern("she")
            .addPattern("his")
            .addPattern("hers")
            .build()
        val match = aho.searchFirst("ushers")
        // It will find "she" first ending at index 3 in "ushers"
        assertNotNull(match)
        assertEquals("she", match?.keyword)
    }

    @Test
    fun searchFirst_noMatch_returnsNull() {
        val aho = AhoCorasick.Builder().addPattern("test").build()
        val match = aho.searchFirst("hello world")
        assertNull(match)
    }

    @Test
    fun searchFirst_matchesPatternsContainingDigits_andReportsEndPosition() {
        val aho = AhoCorasick.Builder().addPattern("v2").build()

        val match = aho.searchFirst("release v2")

        assertNotNull(match)
        assertEquals("v2", match?.keyword)
        assertEquals(9, match?.position)
    }
}
