package com.focuslock.domain.usecase

import com.focuslock.domain.model.FetchOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaDescriptionFetcher @Inject constructor() {

    // Cache: domain → signals (or empty string if none found / failed)
    // Prevents repeat network fetches for the same domain within a session.
    private val descriptionCache = ConcurrentHashMap<String, Map<String, String>>()
    private val MAX_CACHE_SIZE = 200

    suspend fun fetchPageSignals(urlStr: String): FetchOutcome = withContext(Dispatchers.IO) {
        val cacheKey = try {
            val parsed = URL(if (urlStr.startsWith("http")) urlStr else "https://$urlStr")
            parsed.host.lowercase()
        } catch (e: Exception) {
            return@withContext FetchOutcome.Failed("Invalid URL")
        }

        if (descriptionCache.containsKey(cacheKey)) {
            val cached = descriptionCache[cacheKey]
            return@withContext if (cached.isNullOrEmpty()) FetchOutcome.Failed("Cached empty") else FetchOutcome.Success(cached)
        }

        if (descriptionCache.size >= MAX_CACHE_SIZE) {
            val firstKey = descriptionCache.keys().nextElement()
            descriptionCache.remove(firstKey)
        }

        var lastException: Exception? = null
        repeat(2) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                return@withContext withTimeout(4000L) {
                    val url = URL(if (urlStr.startsWith("http")) urlStr else "https://$urlStr")
                    if (isPrivateHost(url.host)) {
                        throw Exception("Blocked: private address")
                    }
                    connection = url.openConnection() as HttpURLConnection
                    connection!!.connectTimeout = 3000
                    connection!!.readTimeout = 3000
                    connection!!.requestMethod = "GET"
                    connection!!.setRequestProperty("User-Agent", "Mozilla/5.0")

                    if (connection!!.responseCode != HttpURLConnection.HTTP_OK) {
                        throw Exception("HTTP error: ${connection!!.responseCode}")
                    } else {
                        val inputStream = connection!!.inputStream
                        val reader = inputStream.bufferedReader()
                        val charBuffer = CharArray(8192)
                        
                        var totalHtml = ""
                        val charsRead1 = reader.read(charBuffer)
                        if (charsRead1 > 0) {
                            totalHtml += String(charBuffer, 0, charsRead1)
                            // read body
                            val charsRead2 = reader.read(charBuffer)
                            if (charsRead2 > 0) {
                                totalHtml += String(charBuffer, 0, charsRead2)
                            }
                        }
                        
                        val signals = extractSignals(totalHtml)
                        descriptionCache[cacheKey] = signals
                        FetchOutcome.Success(signals)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt == 0) delay(500)
            } finally {
                connection?.disconnect()
            }
        }
        
        descriptionCache[cacheKey] = emptyMap()
        FetchOutcome.Failed(lastException?.message ?: "Unknown Error")
    }

    private fun isPrivateHost(host: String): Boolean {
        return try {
            val addr = InetAddress.getByName(host)
            addr.isLoopbackAddress ||
                addr.isSiteLocalAddress ||
                addr.isLinkLocalAddress ||
                addr.isAnyLocalAddress
        } catch (e: Exception) {
            false
        }
    }

    fun extractSignals(html: String): Map<String, String> {
        val doc = Jsoup.parse(html)
        return mapOf(
            "meta_description" to (doc.select("meta[name=description]").attr("content")),
            "meta_keywords" to (doc.select("meta[name=keywords]").attr("content")),
            "og_title" to (doc.select("meta[property=og:title]").attr("content")),
            "og_description" to (doc.select("meta[property=og:description]").attr("content")),
            "twitter_description" to (doc.select("meta[name=twitter:description]").attr("content")),
            "page_title" to (doc.selectFirst("title")?.text().orEmpty()),
            "first_h1" to (doc.selectFirst("h1")?.text().orEmpty()),
            "body_text" to (doc.body()?.text()?.take(2000).orEmpty())
        ).filterValues { it.isNotBlank() }
    }
}
