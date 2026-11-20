package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.model.BlockReason
import com.focuslock.domain.util.SimpleLruCache
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CheckUrlForBlockedContentUseCase @Inject constructor() {

    // ponytail: key cache by keyword contents hash and stable domain revision
    private data class CacheKey(
        val url: String,
        val keywordsHash: Int,
        val domainRevision: Long
    )

    private val cache = SimpleLruCache<CacheKey, BlockCheckResult>(200)

    // 2.2: URL matching must not run on the accessibility callback (Main). withContext keeps
    // the work attached to the caller, so a cancelled caller cancels the check instead of
    // leaking it on an external SupervisorJob scope.
    private val domainSetMutex = Mutex()
    @Volatile private var cachedDomainsRef: List<String>? = null
    @Volatile private var cachedDomainRevision: Long = Long.MIN_VALUE
    @Volatile private var cachedKeywordsHash: Int = Int.MIN_VALUE
    @Volatile private var cachedDomainSet: Set<String> = emptySet()

    // ponytail: clear LRU cache on session state changes or rule updates
    fun clearCache() {
        cache.clear()
        cachedDomainsRef = null
        cachedDomainRevision = Long.MIN_VALUE
        cachedKeywordsHash = Int.MIN_VALUE
        cachedDomainSet = emptySet()
    }

    suspend operator fun invoke(
        url: String,
        keywords: List<String>,
        domains: List<String>,
        domainRevision: Long = 0L
    ): BlockCheckResult {
        if (url.isBlank()) return BlockCheckResult.Allowed

        val keywordsHash = keywords.hashCode()
        val cacheKey = CacheKey(url, keywordsHash, domainRevision)
        val cached = cache.get(cacheKey)
        if (cached != null) return cached

        return withContext(Dispatchers.Default) {
        val uri = try {
            val parsed = URI(url)
            when {
                parsed.scheme.equals("http", ignoreCase = true) ||
                    parsed.scheme.equals("https", ignoreCase = true) -> parsed
                !url.contains("://") -> URI("https://$url")
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        val host = uri?.host
            ?.trimEnd('.')
            ?.lowercase()
            
        if (host.isNullOrEmpty()) {
            val result = BlockCheckResult.Allowed
            cache.put(cacheKey, result)
            return@withContext result
        }

        val path = uri?.path?.lowercase() ?: ""
        val hostAndPath = host + path        
        // 1. Check Keywords
        for (keyword in keywords) {
            if (NormalizedMatcher.matches(keyword, hostAndPath)) {
                val result = BlockCheckResult.Blocked(keyword, BlockReason.KEYWORD_MATCH)
                cache.put(cacheKey, result)
                return@withContext result
            }
        }

        // 2. Check Domains (ponytail: O(labels) set lookup; avoids O(58,000) linear scan on main thread)
        val normalizedHost = if (host.startsWith("www.")) host.substring(4) else host
        val domainSet = getOrRebuildDomainSet(domains, domainRevision, keywordsHash)

        var candidate = normalizedHost
        while (candidate.isNotEmpty()) {
            if (domainSet.contains(candidate)) {
                val result = BlockCheckResult.Blocked(candidate, BlockReason.DOMAIN_BLOCKED)
                cache.put(cacheKey, result)
                return@withContext result
            }
            val dotIndex = candidate.indexOf('.')
            if (dotIndex == -1) break
            candidate = candidate.substring(dotIndex + 1)
        }

        val result = BlockCheckResult.Allowed
        cache.put(cacheKey, result)
        return@withContext result
        }
    }

    private suspend fun getOrRebuildDomainSet(
        domains: List<String>,
        domainRevision: Long,
        keywordsHash: Int
    ): Set<String> = domainSetMutex.withLock {
        if (cachedDomainsRef !== domains ||
            cachedDomainRevision != domainRevision ||
            cachedKeywordsHash != keywordsHash
        ) {
            cachedDomainSet = domains.map { if (it.startsWith("www.")) it.substring(4) else it }.toHashSet()
            cachedDomainsRef = domains
            cachedDomainRevision = domainRevision
            cachedKeywordsHash = keywordsHash
        }
        cachedDomainSet
    }
}
