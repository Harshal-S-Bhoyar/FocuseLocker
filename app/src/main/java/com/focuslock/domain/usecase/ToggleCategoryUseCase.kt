package com.focuslock.domain.usecase

import com.focuslock.data.repository.BlockingRepository
import javax.inject.Inject

/**
 * UseCase to toggle the active state of a blocked category.
 */
class ToggleCategoryUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    suspend operator fun invoke(categoryKey: String, isActive: Boolean): Result<Unit> {
        return try {
            blockingRepository.setCategoryActive(categoryKey, isActive)
            Result.success(Unit)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to toggle category")
            Result.failure(e)
        }
    }
}
