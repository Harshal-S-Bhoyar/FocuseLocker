package com.focuslock.domain.usecase

import android.content.Context
import com.focuslock.domain.model.FetchOutcome
import com.focuslock.domain.model.SmartScanResult
import com.focuslock.domain.repository.BlockingRepository
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class SmartScanUseCase @Inject constructor(
    private val fetcher: MetaDescriptionFetcher,
    private val blockingRepository: BlockingRepository,
    @ApplicationContext private val context: Context
) {
    private val synonymsMap by lazy {
        try {
            val jsonStr = context.assets.open("keyword_synonyms.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, List<String>>()
            for (key in json.keys()) {
                val array = json.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                map[key] = list
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // In-memory cache to prevent excessive DB queries during rapid navigation (Battery Optimization)
    private val memoryCache = LruCache<String, SmartScanResult>(50)

    /**
     * Scans the given URL's meta description for blocked keywords.
     * Returns SmartScanResult with rich match data.
     */
    suspend operator fun invoke(urlStr: String, activeKeywords: List<String>): SmartScanResult {
        if (activeKeywords.isEmpty()) return SmartScanResult.Clean

        val domain = extractDomain(urlStr) ?: return SmartScanResult.Clean

        // Check in-memory cache first
        memoryCache.get(domain)?.let { return it }

        val cachedStatus = blockingRepository.getCachedDomainStatus(domain)
        when (cachedStatus) {
            "PERMANENT" -> {
                val result = SmartScanResult.Blocked("cache", "domain_cache")
                memoryCache.put(domain, result)
                return result
            }
            "CLEAN_CACHE" -> {
                val result = SmartScanResult.Clean
                memoryCache.put(domain, result)
                return result
            }
        }

        val outcome = fetcher.fetchPageSignals(urlStr)
        coroutineContext.ensureActive()

        when (outcome) {
            is FetchOutcome.Failed -> {
                // Don't save failed fetches as blocked — allow the page through.
                // Only keyword-matched pages should be blocked and saved.
                return SmartScanResult.FetchFailed(outcome.reason)
            }
            is FetchOutcome.Success -> {
                val signals = outcome.signals
                
                for (keyword in activeKeywords) {
                    val kw = keyword.lowercase()
                    val expansion = listOf(kw) + (synonymsMap[kw] ?: emptyList())
                    
                    for (term in expansion) {
                        for ((signalName, signalValue) in signals) {
                            if (NormalizedMatcher.matches(term, signalValue)) {
                                blockingRepository.addAutoBlockedDomain(domain, kw, "PERMANENT", null)
                                val result = SmartScanResult.Blocked(kw, signalName)
                                memoryCache.put(domain, result)
                                return result
                            }
                        }
                    }
                }
                
                val expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L // 24 hours
                blockingRepository.addAutoBlockedDomain(domain, "", "CLEAN_CACHE", expiresAt)
                val result = SmartScanResult.Clean
                memoryCache.put(domain, result)
                return result
            }
        }
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
