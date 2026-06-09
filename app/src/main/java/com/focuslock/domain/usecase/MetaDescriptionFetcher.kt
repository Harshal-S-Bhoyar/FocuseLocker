package com.focuslock.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaDescriptionFetcher @Inject constructor() {

    // Cache: domain → meta description (or empty string if none found / failed)
    // Prevents repeat network fetches for the same domain within a session.
    // This is a major battery-saver: without this, every URL navigation triggers a
    // fresh HTTP GET request even for domains we've already checked.
    private val descriptionCache = ConcurrentHashMap<String, String>()
    private val MAX_CACHE_SIZE = 200

    suspend fun fetchMetaDescription(urlStr: String): String? = withContext(Dispatchers.IO) {
        // Extract just the domain as the cache key (ignores query params and paths)
        val cacheKey = try {
            val parsed = URL(if (urlStr.startsWith("http")) urlStr else "https://$urlStr")
            parsed.host.lowercase()
        } catch (e: Exception) {
            return@withContext null
        }

        // Return cached result if we've seen this domain before
        if (descriptionCache.containsKey(cacheKey)) {
            val cached = descriptionCache[cacheKey]
            return@withContext if (cached.isNullOrEmpty()) null else cached
        }

        // Evict oldest entries if cache is too large
        if (descriptionCache.size >= MAX_CACHE_SIZE) {
            val firstKey = descriptionCache.keys().nextElement()
            descriptionCache.remove(firstKey)
        }

        var connection: HttpURLConnection? = null
        val result: String? = try {
            val url = URL(if (urlStr.startsWith("http")) urlStr else "https://$urlStr")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val inputStream = connection.inputStream
                val reader = inputStream.bufferedReader()
                val charBuffer = CharArray(8192) // Read up to 8KB
                val charsRead = reader.read(charBuffer)
                if (charsRead <= 0) {
                    null
                } else {
                    val htmlSnippet = String(charBuffer, 0, charsRead)
                    extractDescriptionFromHtml(htmlSnippet)
                }
            }
        } catch (e: Exception) {
            // Don't log exception details here — it may contain URL info
            null
        } finally {
            connection?.disconnect()
        }

        // Cache and return the result
        descriptionCache[cacheKey] = result ?: ""
        result
    }

    fun extractDescriptionFromHtml(htmlSnippet: String): String? {
        val metaTagPattern = "(?i)<meta([^>]{0,1000})>".toRegex()
        val contentPattern = "(?i)content=[\"'](.*?)[\"']".toRegex()
        val namePattern = "(?i)name=[\"']description[\"']".toRegex()
        
        val matchContent = metaTagPattern.findAll(htmlSnippet)
            .filter { namePattern.containsMatchIn(it.groupValues[1]) }
            .mapNotNull { contentPattern.find(it.groupValues[1])?.groupValues?.get(1) }
            .firstOrNull()

        if (matchContent != null) {
            return matchContent
        } else {
            // Also check property="og:description"
            val ogNamePattern = "(?i)property=[\"']og:description[\"']".toRegex()
            return metaTagPattern.findAll(htmlSnippet)
                .filter { ogNamePattern.containsMatchIn(it.groupValues[1]) }
                .mapNotNull { contentPattern.find(it.groupValues[1])?.groupValues?.get(1) }
                .firstOrNull()
        }
    }
}
