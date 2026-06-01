package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.domain.repository.BlockingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetAllKeywordsUseCaseTest {

    private val blockingRepository = mockk<BlockingRepository>()
    private val useCase = GetAllKeywordsUseCase(blockingRepository)

    @Test
    fun `invoke exposes repository keywords`() = runTest {
        val keywords = listOf(BlockedKeyword(1L, "distraction", true, 100L))
        every { blockingRepository.getAllKeywords() } returns flowOf(keywords)

        assertEquals(keywords, useCase().first())
    }
}
