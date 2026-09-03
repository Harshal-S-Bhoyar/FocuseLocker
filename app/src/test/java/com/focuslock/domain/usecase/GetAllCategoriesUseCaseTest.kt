package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockedCategory
import com.focuslock.data.repository.BlockingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetAllCategoriesUseCaseTest {

    private val blockingRepository = mockk<BlockingRepository>()
    private val useCase = GetAllCategoriesUseCase(blockingRepository)

    @Test
    fun `invoke exposes repository categories`() = runTest {
        val categories = listOf(BlockedCategory(1L, "SOCIAL_MEDIA", true))
        every { blockingRepository.getAllCategories() } returns flowOf(categories)

        assertEquals(categories, useCase().first())
    }

    @Test
    fun `invoke preserves multiple category records`() = runTest {
        val categories = listOf(
            BlockedCategory(1L, "SOCIAL_MEDIA", true),
            BlockedCategory(2L, "GAMING", false)
        )
        every { blockingRepository.getAllCategories() } returns flowOf(categories)

        assertEquals(categories, useCase().first())
    }

    @Test
    fun `invoke emits empty list when repository has no categories`() = runTest {
        every { blockingRepository.getAllCategories() } returns flowOf(emptyList())

        assertEquals(emptyList<BlockedCategory>(), useCase().first())
    }

    @Test
    fun `invoke forwards successive repository emissions`() = runTest {
        val first = listOf(BlockedCategory(1L, "SOCIAL_MEDIA", true))
        val second = listOf(BlockedCategory(2L, "NEWS", true))
        every { blockingRepository.getAllCategories() } returns flowOf(first, second)

        assertEquals(listOf(first, second), useCase().toList())
    }

    @Test
    fun `invoke propagates repository flow failure`() = runTest {
        val failure = IllegalStateException("category database unavailable")
        every { blockingRepository.getAllCategories() } throws failure

        try {
            useCase()
            throw AssertionError("Expected repository failure")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error)
        }
    }
}
