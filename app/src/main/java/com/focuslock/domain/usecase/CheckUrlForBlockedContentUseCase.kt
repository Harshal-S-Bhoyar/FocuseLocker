package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.model.BlockReason
import com.focuslock.domain.util.SimpleLruCache
import java.net.URI
import javax.inject.Inject

class CheckUrlForBlockedContentUseCase @Inject constructor() {

    private val cache = SimpleLruCache<String, BlockCheckResult>(200)

    operator fun invoke(
        url: String,
        keywords: List<String>,
        domains: List<String>
    ): BlockCheckResult {
        if (url.isBlank()) return BlockCheckResult.Allowed

        val cacheKey = "${url}|${keywords.hashCode()}|${domains.hashCode()}"
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
        val host = uri?.host?.lowercase()
        val path = uri?.path?.lowercase() ?: ""
        val hostAndPath = (host ?: "") + path        
        // 1. Check Keywords
        for (keyword in keywords) {
            if (NormalizedMatcher.matches(keyword, hostAndPath)) {
                val result = BlockCheckResult.Blocked(keyword, BlockReason.KEYWORD_MATCH)
                cache.put(cacheKey, result)
                return result
            }
        }

        // 2. Check Domains
        val normalizedHost = if (host != null && host.startsWith("www.")) host.substring(4) else host

        if (normalizedHost != null) {
            for (domain in domains) {
                val lowerDomain = domain.lowercase().let { if (it.startsWith("www.")) it.substring(4) else it }
                // Match exactly or as a subdomain
                if (normalizedHost == lowerDomain || normalizedHost.endsWith(".$lowerDomain")) {
                    val result = BlockCheckResult.Blocked(domain, BlockReason.DOMAIN_BLOCKED)
                    cache.put(cacheKey, result)
                    return result
                }
            }
        }

        val result = BlockCheckResult.Allowed
        cache.put(cacheKey, result)
        return result
    }
}
