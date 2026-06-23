package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockedCategory
import com.focuslock.data.repository.BlockingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
}
