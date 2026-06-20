package com.focuslock.domain.usecase

import android.content.Context
import android.content.res.AssetManager
import com.focuslock.domain.model.FetchOutcome
import com.focuslock.domain.model.SmartScanResult
import com.focuslock.domain.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SmartScanIntegrationTest {

    private lateinit var fetcher: MetaDescriptionFetcher
    private lateinit var blockingRepository: BlockingRepository
    private lateinit var keywordScanner: KeywordScanner
    private lateinit var useCase: SmartScanUseCase
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager

    @Before
    fun setup() {
        context = mockk()
        assetManager = mockk()
        every { context.assets } returns assetManager
        
        // Mock the synonyms JSON to an empty object for test
        val emptyJson = "{}"
        every { assetManager.open("keyword_synonyms.json") } returns ByteArrayInputStream(emptyJson.toByteArray())

        keywordScanner = KeywordScanner(context)
        fetcher = mockk()
        blockingRepository = mockk(relaxed = true)

        useCase = SmartScanUseCase(fetcher, blockingRepository, keywordScanner)
    }

    @Test
    fun `integration - scanning complex URL extracts domain, scans signals, and returns Blocked`() = runTest {
        // 1. Rebuild KeywordScanner with keywords
        keywordScanner.rebuild(listOf("casino", "gambling"))

        // 2. Setup mock repository and fetcher
        val rawUrl = "https://www.mobile.casino-royale.com/play?id=123"
        val expectedDomain = "casino-royale.com" // Extracted by DomainExtractor
        
        coEvery { blockingRepository.getCachedDomainStatus(expectedDomain) } returns null
        coEvery { fetcher.fetchPageSignals(rawUrl) } returns FetchOutcome.Success(
            mapOf("page_description" to "Welcome to the best online casino!")
        )

        // 3. Invoke SmartScanUseCase
        val result = useCase(rawUrl, listOf("casino", "gambling"))

        // 4. Assert DomainExtractor worked, AhoCorasick scanned, and it Blocked
        assertTrue(result is SmartScanResult.Blocked)
        assertEquals("casino", (result as SmartScanResult.Blocked).keyword)
        assertEquals("page_description", result.signal)
    }

    @Test
    fun `integration - caching skips network and returns Blocked`() = runTest {
        keywordScanner.rebuild(listOf("casino", "gambling"))
        
        val rawUrl = "https://gambling.com/games"
        val domain = "gambling.com"
        
        // Simulate domain already in cache
        coEvery { blockingRepository.getCachedDomainStatus(domain) } returns "PERMANENT"
        
        val result = useCase(rawUrl, listOf("casino", "gambling"))
        
        // Should not call fetcher at all
        assertTrue(result is SmartScanResult.Blocked)
        assertEquals("cache", (result as SmartScanResult.Blocked).keyword)
        assertEquals("domain_cache", result.signal)
    }
}
