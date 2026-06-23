package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockedCategory
import com.focuslock.data.repository.BlockingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase to retrieve all available content categories for blocking.
 */
class GetAllCategoriesUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    operator fun invoke(): Flow<List<BlockedCategory>> {
        return blockingRepository.getAllCategories()
    }
}
