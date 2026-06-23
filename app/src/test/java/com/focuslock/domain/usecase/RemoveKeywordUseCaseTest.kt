package com.focuslock.domain.usecase

import com.focuslock.data.repository.BlockingRepository
import io.mockk.coVerify
import io.mockk.mockk
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
}
