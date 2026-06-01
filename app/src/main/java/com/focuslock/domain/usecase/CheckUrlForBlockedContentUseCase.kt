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
            URL(url).host.lowercase()
        } catch (e: Exception) {
            null
        }

        if (host != null) {
            for (domain in domains) {
                val lowerDomain = domain.lowercase()
                // Match exactly or as a subdomain (e.g., host is m.facebook.com and domain is facebook.com)
                if (host == lowerDomain || host.endsWith(".$lowerDomain")) {
                    return BlockCheckResult.Blocked(domain, BlockReason.DOMAIN_BLOCKED)
                }
            }
        }

        return BlockCheckResult.Allowed
    }
}
