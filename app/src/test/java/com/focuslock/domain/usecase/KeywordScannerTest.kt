package com.focuslock.domain.usecase

import com.focuslock.domain.repository.SynonymRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeywordScannerTest {

    private val emptySynonymRepo = object : SynonymRepository {
        override fun getSynonyms(): Map<String, List<String>> = emptyMap()
    }

    @Test
    fun `scan detects long keyword via AhoCorasick`() {
        val scanner = KeywordScanner(emptySynonymRepo)
        scanner.rebuild(listOf("gambling", "casino"))

        val result = scanner.scan(mapOf("content" to "This is a gambling site"))
        assertNotNull(result)
        assertEquals("gambling", result?.first)
    }

    @Test
    fun `scan detects short keyword with word boundary`() {
        val scanner = KeywordScanner(emptySynonymRepo)
        scanner.rebuild(listOf("bet"))

        // Inside word -> should NOT match
        val insideWord = scanner.scan(mapOf("content" to "alphabetical order"))
        assertNull(insideWord)

        // Exact word boundary -> SHOULD match
        val isolated = scanner.scan(mapOf("content" to "place a bet now"))
        assertNotNull(isolated)
        assertEquals("bet", isolated?.first)
    }

    @Test
    fun `short keyword earlier in text does not block long keyword match`() {
        val scanner = KeywordScanner(emptySynonymRepo)
        // 'bet' is short (3 chars), 'gambling' is long (8 chars)
        scanner.rebuild(listOf("bet", "gambling"))

        // 'bet' appears inside 'alphabet', followed by 'gambling'
        val result = scanner.scan(mapOf("content" to "the alphabet order with gambling"))
        assertNotNull(result)
        assertEquals("gambling", result?.first)
    }
}
