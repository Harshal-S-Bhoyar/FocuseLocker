package com.focuslock.domain.usecase

import com.focuslock.domain.model.DoHResult
import com.focuslock.data.repository.BlockingRepository
import com.focuslock.domain.repository.ConfigRepository
import com.focuslock.domain.repository.DoHRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoHQueryUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository,
    private val configRepository: ConfigRepository,
    private val dohRepository: DoHRepository
) {
    suspend fun queryDoH(domain: String): DoHResult {
        return dohRepository.queryDoH(domain)
    }

    suspend fun checkAndCacheDoH(url: String, activeCategories: List<String> = emptyList()): DoHResult {
        if (!configRepository.isAutoModeEnabled()) return DoHResult.Clean

        val domain = DomainExtractor.extract(url) ?: return DoHResult.QueryFailed

        val cachedStatus = blockingRepository.getCachedDomainStatus(domain, activeCategories)
        if (cachedStatus == "PERMANENT") return DoHResult.Blocked
        if (cachedStatus == "CLEAN_CACHE") return DoHResult.Clean

        val result = queryDoH(domain)
        when (result) {
            is DoHResult.Blocked -> {
                blockingRepository.addAutoBlockedDomain(domain, "", "PERMANENT", null)
            }
            is DoHResult.Clean -> {
                val expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
                blockingRepository.addAutoBlockedDomain(domain, "", "CLEAN_CACHE", expiresAt)
            }
            is DoHResult.Unknown, is DoHResult.QueryFailed -> {
                // Do not cache
            }
        }
        return result
    }


}
