package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.model.BlockReason
import com.focuslock.domain.util.SimpleLruCache
import java.net.URI
import javax.inject.Inject

class CheckUrlForBlockedContentUseCase @Inject constructor() {

    // ponytail: key cache by keyword contents hash and stable domain revision
    private data class CacheKey(
        val url: String,
        val keywordsHash: Int,
        val domainRevision: Long
    )

    private val cache = SimpleLruCache<CacheKey, BlockCheckResult>(200)

    // ponytail: clear LRU cache on session state changes or rule updates
    fun clearCache() {
        cache.clear()
    }

    operator fun invoke(
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
            return result
        }

        val path = uri?.path?.lowercase() ?: ""
        val hostAndPath = host + path        
        // 1. Check Keywords
        for (keyword in keywords) {
            if (NormalizedMatcher.matches(keyword, hostAndPath)) {
                val result = BlockCheckResult.Blocked(keyword, BlockReason.KEYWORD_MATCH)
                cache.put(cacheKey, result)
                return result
            }
        }

        // 2. Check Domains (ponytail: O(labels) set lookup; avoids O(58,000) linear scan on main thread)
        val normalizedHost = if (host.startsWith("www.")) host.substring(4) else host
        val domainSet = (domains as? Set<String>) ?: domains.map { if (it.startsWith("www.")) it.substring(4) else it }.toHashSet()

        var candidate = normalizedHost
        while (candidate.isNotEmpty()) {
            if (domainSet.contains(candidate)) {
                val result = BlockCheckResult.Blocked(candidate, BlockReason.DOMAIN_BLOCKED)
                cache.put(cacheKey, result)
                return result
            }
            val dotIndex = candidate.indexOf('.')
            if (dotIndex == -1) break
            candidate = candidate.substring(dotIndex + 1)
        }

        val result = BlockCheckResult.Allowed
        cache.put(cacheKey, result)
        return result
    }
}
