package com.focuslock.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetaDescriptionFetcherTest {

    @Test
    fun testFetchMetaDescription() = runBlocking {
        val fetcher = MetaDescriptionFetcher()
        // Test with a known safe URL that has a meta description, or just verify it doesn't crash on invalid URLs.
        val invalidUrlDesc = fetcher.fetchMetaDescription("invalid-url-that-does-not-exist.com")
        assertNull(invalidUrlDesc)
    }
}
