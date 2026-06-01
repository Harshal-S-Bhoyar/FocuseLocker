package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertTrue

class ToggleCategoryUseCaseTest {

    private val blockingRepository = mockk<BlockingRepository>(relaxed = true)
    private val useCase = ToggleCategoryUseCase(blockingRepository)

    @Test
    fun `invoke calls repository to update category`() = runTest {
        val result = useCase("SOCIAL_MEDIA", true)
        coVerify { blockingRepository.setCategoryActive("SOCIAL_MEDIA", true) }
        assertTrue(result.isSuccess)
    }
}
