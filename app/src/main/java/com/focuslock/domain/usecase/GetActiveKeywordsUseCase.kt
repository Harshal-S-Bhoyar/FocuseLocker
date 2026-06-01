package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetActiveKeywordsUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    operator fun invoke(): Flow<List<String>> {
        return blockingRepository.getActiveKeywords().map { keywords ->
            keywords.map { it.keyword }
        }
    }
}
