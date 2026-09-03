package com.focuslock.domain.usecase

import com.focuslock.data.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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

    @Test
    fun `invoke supports deactivating a category`() = runTest {
        val result = useCase("SOCIAL_MEDIA", false)

        assertTrue(result.isSuccess)
        coVerify { blockingRepository.setCategoryActive("SOCIAL_MEDIA", false) }
    }

    @Test
    fun `invoke forwards an empty category key to repository`() = runTest {
        val result = useCase("", true)

        assertTrue(result.isSuccess)
        coVerify { blockingRepository.setCategoryActive("", true) }
    }

    @Test
    fun `invoke returns failure when repository update fails`() = runTest {
        val failure = IllegalStateException("category update failed")
        coEvery { blockingRepository.setCategoryActive("GAMING", true) } throws failure

        val result = useCase("GAMING", true)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
    }

    @Test
    fun `invoke converts cancellation exception to failure result`() = runTest {
        val failure = CancellationException("cancelled")
        coEvery { blockingRepository.setCategoryActive("NEWS", false) } throws failure

        val result = useCase("NEWS", false)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
    }
}
