package com.focuslock.domain.usecase

import com.focuslock.data.repository.BlockingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllBlockedDomainsFlowUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    operator fun invoke(): Flow<List<String>> {
        return blockingRepository.getAllBlockedDomainsFlow()
    }
}
