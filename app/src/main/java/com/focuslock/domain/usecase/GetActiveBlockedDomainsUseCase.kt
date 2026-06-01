package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
import javax.inject.Inject

class GetActiveBlockedDomainsUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    suspend operator fun invoke(): List<String> {
        return blockingRepository.getActiveBlockedDomains()
    }
}
