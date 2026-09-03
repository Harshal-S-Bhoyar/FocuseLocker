package com.focuslock.domain.usecase

import com.focuslock.domain.repository.PageSignalFetcher
import com.focuslock.domain.model.FetchOutcome
import com.focuslock.domain.model.SmartScanResult
import com.focuslock.data.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.coVerify
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

    private lateinit var fetcher: PageSignalFetcher
    private lateinit var blockingRepository: BlockingRepository
    private lateinit var keywordScanner: KeywordScanner
    private lateinit var useCase: SmartScanUseCase
    private lateinit var synonymRepository: com.focuslock.domain.repository.SynonymRepository

    @Before
    fun setup() {
        synonymRepository = mockk()
        every { synonymRepository.getSynonyms() } returns emptyMap()
        
        keywordScanner = KeywordScanner(synonymRepository)
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

    @Test
    fun `integration - empty active keyword list fails open without fetching`() = runTest {
        val result = useCase("https://example.com/page", emptyList())

        assertEquals(SmartScanResult.Clean, result)
        coVerify(exactly = 0) { fetcher.fetchPageSignals(any()) }
    }

    @Test
    fun `integration - invalid URL fails open without repository or network access`() = runTest {
        val result = useCase("https://[invalid", listOf("casino"))

        assertEquals(SmartScanResult.Clean, result)
        coVerify(exactly = 0) { fetcher.fetchPageSignals(any()) }
        coVerify(exactly = 0) { blockingRepository.getCachedDomainStatus(any()) }
    }

    @Test
    fun `integration - search-engine domains are skipped`() = runTest {
        val result = useCase("https://www.google.com/search?q=casino", listOf("casino"))

        assertEquals(SmartScanResult.Clean, result)
        coVerify(exactly = 0) { fetcher.fetchPageSignals(any()) }
        coVerify(exactly = 0) { blockingRepository.getCachedDomainStatus(any()) }
    }

    @Test
    fun `integration - incomplete fetch returns a cached fetch failure`() = runTest {
        val url = "https://incomplete.example.com/page"
        coEvery { fetcher.fetchPageSignals(url) } returns FetchOutcome.FetchEmpty

        val first = useCase(url, listOf("casino"))
        assertEquals(SmartScanResult.FetchFailed("fetch_incomplete"), first)

        coEvery { fetcher.fetchPageSignals(url) } returns FetchOutcome.Success(
            mapOf("page_description" to "casino")
        )
        val second = useCase(url, listOf("casino"))

        assertEquals(first, second)
        coVerify(exactly = 1) { fetcher.fetchPageSignals(url) }
    }

    @Test
    fun `integration - fetch error reason is propagated as a fail-open result`() = runTest {
        val url = "https://error.example.com/page"
        coEvery { fetcher.fetchPageSignals(url) } returns FetchOutcome.FetchError("timeout")

        val result = useCase(url, listOf("casino"))

        assertEquals(SmartScanResult.FetchFailed("timeout"), result)
    }

    @Test
    fun `integration - database failure returns a stable database error`() = runTest {
        val url = "https://database.example.com/page"
        coEvery { blockingRepository.getCachedDomainStatus("database.example.com") } throws
            IllegalStateException("database unavailable")

        val result = useCase(url, listOf("casino"))

        assertEquals(SmartScanResult.FetchFailed("db_error"), result)
        coVerify(exactly = 0) { fetcher.fetchPageSignals(any()) }
    }

    @Test
    fun `integration - clean page result is cached and prevents a later fetch`() = runTest {
        keywordScanner.rebuild(listOf("casino"))
        val url = "https://clean.example.com/page"
        coEvery { fetcher.fetchPageSignals(url) } returns FetchOutcome.Success(
            mapOf("page_description" to "a calm reading page")
        )

        val first = useCase(url, listOf("casino"))
        assertEquals(SmartScanResult.Clean, first)

        coEvery { fetcher.fetchPageSignals(url) } returns FetchOutcome.Success(
            mapOf("page_description" to "casino")
        )
        val second = useCase(url, listOf("casino"))

        assertEquals(SmartScanResult.Clean, second)
        coVerify(exactly = 1) { fetcher.fetchPageSignals(url) }
    }
}
