package com.focuslock.domain.usecase

import com.focuslock.data.repository.BlockingRepository
import javax.inject.Inject

/**
 * UseCase to add a new custom blocked keyword.
 * Enforces the maximum 50 keyword limit and ensures lowercase formatting.
 */
class AddKeywordUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    suspend operator fun invoke(keyword: String): Result<Unit> {
        val count = blockingRepository.getKeywordCount()
        if (count >= 50) {
            return Result.failure(IllegalStateException("Maximum of 50 keywords allowed."))
        }
        val lowercaseKeyword = keyword.trim().lowercase()
        if (lowercaseKeyword.isEmpty()) {
            return Result.failure(IllegalArgumentException("Keyword cannot be empty."))
        }
        return blockingRepository.addKeyword(lowercaseKeyword)
    }
}
