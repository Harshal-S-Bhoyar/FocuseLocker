package com.focuslock.domain.usecase

import android.content.Context
import android.content.res.AssetManager
import com.focuslock.domain.model.FetchOutcome
import com.focuslock.domain.model.SmartScanResult
import com.focuslock.domain.repository.BlockingRepository
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
class SmartScanUseCaseTest {

    private lateinit var fetcher: MetaDescriptionFetcher
    private lateinit var blockingRepository: BlockingRepository
    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var useCase: SmartScanUseCase

    @Before
    fun setup() {
        fetcher = mockk()
        blockingRepository = mockk(relaxed = true)
        context = mockk()
        assetManager = mockk()

        val emptyJson = "{}"
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } returns ByteArrayInputStream(emptyJson.toByteArray())

        useCase = SmartScanUseCase(fetcher, blockingRepository, context)
    }

    @Test
    fun `invoke with empty activeKeywords returns Clean`() = runTest {
        val result = useCase("google.com", emptyList())
        assertEquals(SmartScanResult.Clean, result)
    }

    @Test
    fun `invoke with cached PERMANENT returns Blocked`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("google.com") } returns "PERMANENT"
        
        val result = useCase("google.com", listOf("casino"))
        assertTrue(result is SmartScanResult.Blocked)
        assertEquals("cache", (result as SmartScanResult.Blocked).keyword)
    }

    @Test
    fun `invoke with cached CLEAN_CACHE returns Clean`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("google.com") } returns "CLEAN_CACHE"
        
        val result = useCase("google.com", listOf("casino"))
        assertEquals(SmartScanResult.Clean, result)
    }

    @Test
    fun `invoke with FetchFailed does NOT save domain and returns FetchFailed`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("google.com") } returns null
        coEvery { fetcher.fetchPageSignals(any()) } returns FetchOutcome.Failed("Timeout")

        val result = useCase("google.com", listOf("casino"))
        
        assertTrue(result is SmartScanResult.FetchFailed)
        assertEquals("Timeout", (result as SmartScanResult.FetchFailed).reason)
        
        // Verify NO domain was saved — fetch failures should not block
        coVerify(exactly = 0) { 
            blockingRepository.addAutoBlockedDomain(any(), any(), any(), any()) 
        }
    }

    @Test
    fun `invoke with FetchSuccess and Match persists PERMANENT and returns Blocked`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("google.com") } returns null
        coEvery { fetcher.fetchPageSignals(any()) } returns FetchOutcome.Success(
            mapOf("page_title" to "Best Casino Online")
        )

        val result = useCase("google.com", listOf("casino"))
        
        assertTrue(result is SmartScanResult.Blocked)
        assertEquals("casino", (result as SmartScanResult.Blocked).keyword)
        
        coVerify { 
            blockingRepository.addAutoBlockedDomain(
                "google.com", 
                "casino", 
                "PERMANENT", 
                null
            ) 
        }
    }

    @Test
    fun `invoke with FetchSuccess and no Match persists CLEAN_CACHE and returns Clean`() = runTest {
        coEvery { blockingRepository.getCachedDomainStatus("google.com") } returns null
        coEvery { fetcher.fetchPageSignals(any()) } returns FetchOutcome.Success(
            mapOf("page_title" to "Safe Website")
        )

        val result = useCase("google.com", listOf("casino"))
        
        assertEquals(SmartScanResult.Clean, result)
        
        coVerify { 
            blockingRepository.addAutoBlockedDomain(
                "google.com", 
                "", 
                "CLEAN_CACHE", 
                any()
            ) 
        }
    }
}
