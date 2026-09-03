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

class GetAllKeywordsUseCaseTest {

    private val blockingRepository = mockk<BlockingRepository>()
    private val useCase = GetAllKeywordsUseCase(blockingRepository)

    @Test
    fun `invoke exposes repository keywords`() = runTest {
        val keywords = listOf(BlockedKeyword(1L, "distraction", true, 100L))
        every { blockingRepository.getAllKeywords() } returns flowOf(keywords)

        assertEquals(keywords, useCase().first())
    }

    @Test
    fun `invoke preserves multiple keyword records`() = runTest {
        val keywords = listOf(
            BlockedKeyword(1L, "first", true, 100L),
            BlockedKeyword(2L, "second", false, 200L)
        )
        every { blockingRepository.getAllKeywords() } returns flowOf(keywords)

        assertEquals(keywords, useCase().first())
    }

    @Test
    fun `invoke emits empty list when repository is empty`() = runTest {
        every { blockingRepository.getAllKeywords() } returns flowOf(emptyList())

        assertEquals(emptyList<BlockedKeyword>(), useCase().first())
    }

    @Test
    fun `invoke forwards each repository flow emission`() = runTest {
        val first = listOf(BlockedKeyword(1L, "first", true, 100L))
        val second = listOf(BlockedKeyword(2L, "second", true, 200L))
        every { blockingRepository.getAllKeywords() } returns flowOf(first, second)

        assertEquals(listOf(first, second), useCase().toList())
    }

    @Test
    fun `invoke propagates repository flow failure`() = runTest {
        val failure = IllegalStateException("keyword database unavailable")
        every { blockingRepository.getAllKeywords() } throws failure

        try {
            useCase()
            throw AssertionError("Expected repository failure")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error)
        }
    }
}
