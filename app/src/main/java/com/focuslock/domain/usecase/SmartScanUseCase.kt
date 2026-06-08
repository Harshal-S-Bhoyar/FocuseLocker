package com.focuslock.domain.usecase

import com.focuslock.domain.repository.BlockingRepository
import com.focuslock.domain.model.BlockedKeyword
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartScanUseCase @Inject constructor(
    private val fetcher: MetaDescriptionFetcher,
    private val blockingRepository: BlockingRepository
) {
    /**
     * Scans the given URL's meta description for blocked keywords.
     * Returns true if a keyword matched and the domain was auto-blocked.
     *
     * @param urlStr The full URL to scan
     * @param activeKeywords Live keyword list from FocusAccessibilityService
     *        (already maintained via Flow collector — always current)
     */
    suspend operator fun invoke(urlStr: String, activeKeywords: List<String>): Boolean {
        if (activeKeywords.isEmpty()) return false

        val domain = extractDomain(urlStr) ?: return false

        // Fetch the page's meta description (cached per domain inside fetcher)
        val description = fetcher.fetchMetaDescription(urlStr) ?: return false
        val descriptionLower = description.lowercase()

        // Match any keyword against the meta description
        for (keyword in activeKeywords) {
            val kw = keyword.lowercase()
            if (descriptionLower.contains(kw)) {
                // Match found — save this domain as auto-blocked
                blockingRepository.addAutoBlockedDomain(domain, kw)
                return true
            }
        }

        return false
    }

    private fun extractDomain(url: String): String? {
        try {
            val uri = java.net.URI(if (url.startsWith("http")) url else "https://$url")
            val host = uri.host ?: return null
            return if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            return null
        }
    }
}
