package com.focuslock.domain.usecase

import android.net.Uri
import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.model.BlockReason
import java.net.URL
import javax.inject.Inject

class CheckUrlForBlockedContentUseCase @Inject constructor() {

    operator fun invoke(
        url: String,
        keywords: List<String>,
        domains: List<String>
    ): BlockCheckResult {
        if (url.isBlank()) return BlockCheckResult.Allowed

        val lowerUrl = url.lowercase()
        
        // 1. Check Keywords
        for (keyword in keywords) {
            if (lowerUrl.contains(keyword.lowercase())) {
                return BlockCheckResult.Blocked(keyword, BlockReason.KEYWORD_MATCH)
            }
        }

        // 2. Check Domains
        val host = try {
            val uri = java.net.URI(if (url.startsWith("http")) url else "https://$url")
            val h = uri.host?.lowercase()
            if (h != null && h.startsWith("www.")) h.substring(4) else h
        } catch (e: Exception) {
            null
        }

        if (host != null) {
            for (domain in domains) {
                val lowerDomain = domain.lowercase().let { if (it.startsWith("www.")) it.substring(4) else it }
                // Match exactly or as a subdomain (e.g., host is m.facebook.com and domain is facebook.com)
                if (host == lowerDomain || host.endsWith(".$lowerDomain")) {
                    return BlockCheckResult.Blocked(domain, BlockReason.DOMAIN_BLOCKED)
                }
            }
        }

        return BlockCheckResult.Allowed
    }
}
