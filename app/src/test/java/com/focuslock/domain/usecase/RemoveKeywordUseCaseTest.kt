package com.focuslock.domain.usecase

import com.focuslock.data.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveKeywordUseCaseTest {

    private val blockingRepository = mockk<BlockingRepository>(relaxed = true)
    private val useCase = RemoveKeywordUseCase(blockingRepository)

    @Test
    fun `invoke removes keyword by id`() = runTest {
        val result = useCase(7L)

        assertTrue(result.isSuccess)
        coVerify { blockingRepository.removeKeyword(7L) }
    }

    @Test
    fun `invoke succeeds for zero identifier without changing repository contract`() = runTest {
        val result = useCase(0L)

        assertTrue(result.isSuccess)
        coVerify { blockingRepository.removeKeyword(0L) }
    }

    @Test
    fun `invoke succeeds for negative identifier when repository accepts it`() = runTest {
        val result = useCase(-1L)

        assertTrue(result.isSuccess)
        coVerify { blockingRepository.removeKeyword(-1L) }
    }

    @Test
    fun `invoke returns failure when repository removal fails`() = runTest {
        val failure = IllegalStateException("delete failed")
        coEvery { blockingRepository.removeKeyword(8L) } throws failure

        val result = useCase(8L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
    }

    @Test
    fun `invoke converts cancellation exception to failure result`() = runTest {
        val failure = CancellationException("cancelled")
        coEvery { blockingRepository.removeKeyword(9L) } throws failure

        val result = useCase(9L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() === failure)
    }
}
