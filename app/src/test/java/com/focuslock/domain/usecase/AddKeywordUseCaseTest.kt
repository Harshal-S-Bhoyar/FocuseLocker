package com.focuslock.domain.usecase

import com.focuslock.data.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AddKeywordUseCaseTest {

    private val blockingRepository = mockk<BlockingRepository>(relaxed = true)
    private val useCase = AddKeywordUseCase(blockingRepository)

    @Test
    fun `invoke fails if keyword is blank`() = runTest {
        val result = useCase("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("empty") == true)
    }

    @Test
    fun `invoke fails if total keywords reach limit`() = runTest {
        coEvery { blockingRepository.getKeywordCount() } returns 50
        val result = useCase("distraction")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Maximum") == true)
    }

    @Test
    fun `invoke succeeds and converts to lowercase`() = runTest {
        coEvery { blockingRepository.getKeywordCount() } returns 10
        coEvery { blockingRepository.addKeyword(any()) } returns Result.success(Unit)
        val result = useCase("Distraction")
        assertTrue(result.isSuccess)
        coVerify { blockingRepository.addKeyword("distraction") }
    }

    @Test
    fun `invoke accepts the final available keyword slot and trims input`() = runTest {
        coEvery { blockingRepository.getKeywordCount() } returns 49
        coEvery { blockingRepository.addKeyword("focus") } returns Result.success(Unit)

        val result = useCase("  Focus  ")

        assertTrue(result.isSuccess)
        coVerify { blockingRepository.addKeyword("focus") }
    }

    @Test
    fun `invoke returns repository failure unchanged`() = runTest {
        val failure = IllegalStateException("storage failure")
        coEvery { blockingRepository.getKeywordCount() } returns 0
        coEvery { blockingRepository.addKeyword("focus") } returns Result.failure(failure)

        val result = useCase("focus")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
    }
}
