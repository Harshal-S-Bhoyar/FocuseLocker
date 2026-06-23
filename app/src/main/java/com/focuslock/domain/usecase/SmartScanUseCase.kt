package com.focuslock.domain.usecase

import com.focuslock.domain.model.FetchOutcome
import com.focuslock.domain.model.SmartScanResult
import com.focuslock.data.repository.BlockingRepository
import android.util.LruCache
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class SmartScanUseCase @Inject constructor(
    private val fetcher: MetaDescriptionFetcher,
    private val blockingRepository: BlockingRepository,
    private val keywordScanner: KeywordScanner
) {

    // Domains that are inherently search infrastructure — meta-fetch provides no signal.
    // SmartScan is skipped for these. Keyword blocking (CheckUrlForBlockedContentUseCase)
    // still applies to the full URL path (e.g. google.com/search?q=casino).
    private val SEARCH_ENGINE_DOMAINS = setOf(
        "google.com", "bing.com", "duckduckgo.com", "yahoo.com",
        "yandex.com", "baidu.com", "ecosia.org", "startpage.com",
        "search.brave.com", "qwant.com", "mojeek.com", "swisscows.com",
        "you.com", "perplexity.ai"
    )

    // In-memory cache to prevent excessive DB queries during rapid navigation (Battery Optimization)
    private data class CacheEntry(val result: SmartScanResult, val timestamp: Long)
    private val memoryCache = LruCache<String, CacheEntry>(50)
    private val CACHE_TTL_MS = 15 * 60 * 1000L  // 15 minutes

    /**
     * Scans the given URL's meta description for blocked keywords.
     * Returns SmartScanResult with rich match data.
     */
    suspend operator fun invoke(urlStr: String, activeKeywords: List<String>): SmartScanResult {
        if (activeKeywords.isEmpty()) return SmartScanResult.Clean

        val domain = DomainExtractor.extract(urlStr) ?: return SmartScanResult.Clean

        // Skip SmartScan for search engines — they are JS-rendered and unfetchable.
        // Keyword matching on the URL path still applies via CheckUrlForBlockedContentUseCase.
        if (SEARCH_ENGINE_DOMAINS.any { domain == it || domain.endsWith(".$it") }) {
            return SmartScanResult.Clean
        }

        // Check in-memory cache first
        memoryCache.get(domain)?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                return entry.result
            } else {
                memoryCache.remove(domain)
            }
        }

        val cachedStatus = try {
            blockingRepository.getCachedDomainStatus(domain)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Database error retrieving domain status")
            return SmartScanResult.FetchFailed("db_error")
        }
        
        when (cachedStatus) {
            "PERMANENT" -> {
                val result = SmartScanResult.Blocked("cache", "domain_cache")
                memoryCache.put(domain, CacheEntry(result, System.currentTimeMillis()))
                return result
            }
            "CLEAN_CACHE" -> {
                val result = SmartScanResult.Clean
                memoryCache.put(domain, CacheEntry(result, System.currentTimeMillis()))
                return result
            }
        }

        val outcome = fetcher.fetchPageSignals(urlStr)
        coroutineContext.ensureActive()

        when (outcome) {
            is FetchOutcome.FetchTimeout, is FetchOutcome.FetchEmpty -> {
                // FAIL-OPEN: No content evidence available — do NOT block.
                // Re-scan on next visit. Cache nothing to avoid stale false-negatives.
                return SmartScanResult.FetchFailed("fetch_incomplete")
            }
            is FetchOutcome.FetchError -> {
                // Network error — allow, do NOT cache (will re-scan next visit)
                return SmartScanResult.FetchFailed(outcome.reason)
            }
            is FetchOutcome.Success -> {
                val signals = outcome.signals
                val match = keywordScanner.scan(signals)

                if (match != null) {
                    val (kw, signalName) = match
                    blockingRepository.addAutoBlockedDomain(domain, kw, "PERMANENT", null)
                    val result = SmartScanResult.Blocked(kw, signalName)
                    memoryCache.put(domain, CacheEntry(result, System.currentTimeMillis()))
                    return result
                }

                val expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L // 24 hours
                blockingRepository.addAutoBlockedDomain(domain, "", "CLEAN_CACHE", expiresAt)
                val result = SmartScanResult.Clean
                memoryCache.put(domain, CacheEntry(result, System.currentTimeMillis()))
                return result
            }
        }
    }


}
