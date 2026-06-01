package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.domain.repository.BlockingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase to retrieve all blocked keywords added by the user.
 */
class GetAllKeywordsUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    operator fun invoke(): Flow<List<BlockedKeyword>> {
        return blockingRepository.getAllKeywords()
    }
}
