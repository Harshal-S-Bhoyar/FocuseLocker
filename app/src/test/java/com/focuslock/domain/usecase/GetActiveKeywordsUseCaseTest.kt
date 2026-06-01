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

class GetActiveKeywordsUseCaseTest {

    private val blockingRepository = mockk<BlockingRepository>()
    private val useCase = GetActiveKeywordsUseCase(blockingRepository)

    @Test
    fun `invoke exposes only keyword text`() = runTest {
        every { blockingRepository.getActiveKeywords() } returns flowOf(
            listOf(BlockedKeyword(1L, "distraction", true, 100L))
        )

        assertEquals(listOf("distraction"), useCase().first())
    }
}
