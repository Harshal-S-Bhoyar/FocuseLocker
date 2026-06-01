package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
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
            Result.failure(e)
        }
    }
}
