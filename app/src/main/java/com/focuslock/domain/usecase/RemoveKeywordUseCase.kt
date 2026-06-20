package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
import javax.inject.Inject

/**
 * UseCase to remove a blocked keyword.
 */
class RemoveKeywordUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    suspend operator fun invoke(keywordId: Long): Result<Unit> {
        return try {
            blockingRepository.removeKeyword(keywordId)
            Result.success(Unit)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error removing keyword")
            Result.failure(e)
        }
    }
}
