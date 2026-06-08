package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
import javax.inject.Inject

class GetActiveBlockedDomainsUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository
) {
    /**
     * Returns ALL blocked domains — both static (from JSON) and auto-blocked.
     * When AutoBlock is enabled, every domain in the DB should be treated as blocked.
     */
    suspend operator fun invoke(): List<String> {
        return blockingRepository.getAllBlockedDomains()
    }
}
