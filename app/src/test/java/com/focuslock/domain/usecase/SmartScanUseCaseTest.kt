package com.focuslock.domain.usecase

import android.content.Context
import android.content.res.AssetManager
import com.focuslock.domain.model.FetchOutcome
import com.focuslock.domain.model.SmartScanResult
import com.focuslock.data.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

import com.focuslock.domain.repository.PageSignalFetcher

@OptIn(ExperimentalCoroutinesApi::class)
class SmartScanUseCaseTest {

    private lateinit var fetcher: PageSignalFetcher
    private lateinit var blockingRepository: BlockingRepository
    private lateinit var keywordScanner: KeywordScanner
    private lateinit var useCase: SmartScanUseCase

    @Before
    fun setup() {
        fetcher = mockk()
        blockingRepository = mockk(relaxed = true)
        keywordScanner = mockk()

        useCase = SmartScanUseCase(fetcher, blockingRepository, keywordScanner)
    }

    @Test
    fun `invoke with empty activeKeywords returns Clean`() = runTest {
        val result = useCase("google.com", emptyList())
        assertEquals(SmartScanResult.Clean, result)
    }

    @Test
    fun `invoke with cached PERMANENT returns Blocked`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("example.com") } returns "PERMANENT"
        
        val result = useCase("example.com", listOf("casino"))
        assertTrue(result is SmartScanResult.Blocked)
        assertEquals("cache", (result as SmartScanResult.Blocked).keyword)
    }

    @Test
    fun `invoke with cached CLEAN_CACHE returns Clean`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("example.com") } returns "CLEAN_CACHE"
        
        val result = useCase("example.com", listOf("casino"))
        assertEquals(SmartScanResult.Clean, result)
    }

    @Test
    fun `invoke with FetchFailed does NOT save domain and returns FetchFailed`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("example.com") } returns null
        coEvery { fetcher.fetchPageSignals(any()) } returns FetchOutcome.FetchError("Timeout")

        val result = useCase("example.com", listOf("casino"))
        
        assertTrue(result is SmartScanResult.FetchFailed)
        assertEquals("Timeout", (result as SmartScanResult.FetchFailed).reason)
        
        // Verify NO domain was saved — fetch failures should not block
        coVerify(exactly = 0) { 
            blockingRepository.addAutoBlockedDomain(any(), any(), any(), any()) 
        }
    }

    @Test
    fun `invoke with FetchSuccess and Match persists PERMANENT and returns Blocked`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("example.com") } returns null
        coEvery { fetcher.fetchPageSignals(any()) } returns FetchOutcome.Success(
            mapOf("page_title" to "Best Casino Online")
        )
        every { keywordScanner.scan(any()) } returns Pair("casino", "page_title")

        val result = useCase("example.com", listOf("casino"))
        
        assertTrue(result is SmartScanResult.Blocked)
        assertEquals("casino", (result as SmartScanResult.Blocked).keyword)
        
        coVerify { 
            blockingRepository.addAutoBlockedDomain(
                "example.com", 
                "casino", 
                "PERMANENT", 
                null
            ) 
        }
    }

    @Test
    fun `invoke with FetchSuccess and no Match persists CLEAN_CACHE and returns Clean`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("example.com") } returns null
        coEvery { fetcher.fetchPageSignals(any()) } returns FetchOutcome.Success(
            mapOf("page_title" to "Safe Website")
        )
        every { keywordScanner.scan(any()) } returns null

        val result = useCase("example.com", listOf("casino"))
        
        assertEquals(SmartScanResult.Clean, result)
        
        coVerify { 
            blockingRepository.addAutoBlockedDomain(
                "example.com", 
                "", 
                "CLEAN_CACHE", 
                any()
            ) 
        }
    }
    @Test
    fun `invoke with FetchTimeout returns FetchFailed and does not block`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("example.com") } returns null
        coEvery { fetcher.fetchPageSignals(any()) } returns FetchOutcome.FetchTimeout

        val result = useCase("example.com", listOf("casino"))
        
        assertTrue(result is SmartScanResult.FetchFailed)
        assertEquals("fetch_incomplete", (result as SmartScanResult.FetchFailed).reason)
        
        coVerify(exactly = 0) { 
            blockingRepository.addAutoBlockedDomain(any(), any(), any(), any()) 
        }
    }

    @Test
    fun `invoke with FetchEmpty returns FetchFailed and does not block`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("example.com") } returns null
        coEvery { fetcher.fetchPageSignals(any()) } returns FetchOutcome.FetchEmpty

        val result = useCase("example.com", listOf("casino"))
        
        assertTrue(result is SmartScanResult.FetchFailed)
        assertEquals("fetch_incomplete", (result as SmartScanResult.FetchFailed).reason)
        
        coVerify(exactly = 0) { 
            blockingRepository.addAutoBlockedDomain(any(), any(), any(), any()) 
        }
    }

    @Test
    fun `invoke with search engine domain returns Clean without fetching`() = runTest {
        val result = useCase("google.com", listOf("casino"))
        
        assertEquals(SmartScanResult.Clean, result)
        
        coVerify(exactly = 0) { fetcher.fetchPageSignals(any()) }
    }

    @Test
    fun `invoke with DB Exception returns FetchFailed`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus(any()) } throws RuntimeException("DB offline")

        val result = useCase("example.com", listOf("casino"))

        assertTrue(result is SmartScanResult.FetchFailed)
        assertEquals("db_error", (result as SmartScanResult.FetchFailed).reason)
        coVerify(exactly = 0) { fetcher.fetchPageSignals(any()) }
    }

    @Test
    fun `invoke cancellation throws CancellationException`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus(any()) } returns null
        coEvery { fetcher.fetchPageSignals(any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            FetchOutcome.Success(mapOf("page_title" to "Safe"))
        }
        
        val job = launch {
            useCase("example.com", listOf("casino"))
        }
        
        job.cancel()
        
        // Wait for cancellation
        try {
            job.join()
        } catch(e: CancellationException) {
            // expected
        }
        // Verification: If it threw cancellation, it should not have saved anything.
        coVerify(exactly = 0) { blockingRepository.addAutoBlockedDomain(any(), any(), any(), any()) }
    }
}
