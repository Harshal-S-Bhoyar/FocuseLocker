package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
import io.mockk.coEvery
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
}
