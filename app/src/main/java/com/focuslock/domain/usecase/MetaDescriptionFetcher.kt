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

                    // Check <meta name="description" content="...">
                    val pattern = "(?i)<meta[^>]{0,500}name=[\"']description[\"'][^>]{0,500}content=[\"'](.*?)[\"']".toRegex()
                    val match = pattern.find(htmlSnippet)
                    if (match != null) {
                        match.groupValues[1]
                    } else {
                        // Also check property="og:description"
                        val ogPattern = "(?i)<meta[^>]{0,500}property=[\"']og:description[\"'][^>]{0,500}content=[\"'](.*?)[\"']".toRegex()
                        ogPattern.find(htmlSnippet)?.groupValues?.get(1)
                    }
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
}
