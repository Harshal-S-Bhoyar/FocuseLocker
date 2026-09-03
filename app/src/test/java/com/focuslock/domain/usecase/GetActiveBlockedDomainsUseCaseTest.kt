package com.focuslock.domain.usecase

import com.focuslock.data.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetActiveBlockedDomainsUseCaseTest {

    private val blockingRepository = mockk<BlockingRepository>()
    private val useCase = GetActiveBlockedDomainsUseCase(blockingRepository)

    @Test
    fun `invoke exposes active blocked domains`() = runTest {
        val domains = listOf("example.com", "social.example")
        coEvery { blockingRepository.getAllBlockedDomains() } returns domains

        assertEquals(domains, useCase())
    }

    @Test
    fun `invoke returns empty list when no domains are active`() = runTest {
        coEvery { blockingRepository.getAllBlockedDomains() } returns emptyList()

        assertEquals(emptyList<String>(), useCase())
    }

    @Test
    fun `invoke preserves repository ordering and duplicates`() = runTest {
        val domains = listOf("z.example", "a.example", "z.example")
        coEvery { blockingRepository.getAllBlockedDomains() } returns domains

        assertEquals(domains, useCase())
    }

    @Test
    fun `invoke propagates repository failure`() = runTest {
        val failure = IllegalStateException("database unavailable")
        coEvery { blockingRepository.getAllBlockedDomains() } throws failure

        try {
            useCase()
            throw AssertionError("Expected repository failure")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error)
        }
    }

    @Test
    fun `invoke delegates exactly once`() = runTest {
        coEvery { blockingRepository.getAllBlockedDomains() } returns listOf("one.example")

        useCase()

        coVerify(exactly = 1) { blockingRepository.getAllBlockedDomains() }
    }
}
