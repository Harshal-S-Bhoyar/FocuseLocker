package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.data.repository.BlockingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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

    @Test
    fun `invoke maps multiple keywords in repository order`() = runTest {
        every { blockingRepository.getActiveKeywords() } returns flowOf(
            listOf(
                BlockedKeyword(1L, "zeta", true, 100L),
                BlockedKeyword(2L, "alpha", true, 200L)
            )
        )

        assertEquals(listOf("zeta", "alpha"), useCase().first())
    }

    @Test
    fun `invoke emits empty list for empty repository result`() = runTest {
        every { blockingRepository.getActiveKeywords() } returns flowOf(emptyList())

        assertEquals(emptyList<String>(), useCase().first())
    }

    @Test
    fun `invoke maps each flow emission independently`() = runTest {
        every { blockingRepository.getActiveKeywords() } returns flowOf(
            listOf(BlockedKeyword(1L, "first", true, 1L)),
            emptyList()
        )

        assertEquals(listOf(listOf("first"), emptyList()), useCase().toList())
    }

    @Test
    fun `invoke propagates repository flow failure`() = runTest {
        val failure = IllegalStateException("keyword source unavailable")
        every { blockingRepository.getActiveKeywords() } throws failure

        try {
            useCase()
            throw AssertionError("Expected repository failure")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error)
        }
    }
}
