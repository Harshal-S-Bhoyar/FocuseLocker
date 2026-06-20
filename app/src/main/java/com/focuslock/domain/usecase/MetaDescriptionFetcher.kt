package com.focuslock.domain.usecase

import com.focuslock.domain.model.FetchOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.URL
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaDescriptionFetcher @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    // Cache: domain → signals (or empty string if none found / failed)
    // Prevents repeat network fetches for the same domain within a session.
    private val descriptionCache = object : LinkedHashMap<String, Map<String, String>>(
        200, 0.75f, true  // accessOrder = true → LRU
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>): Boolean {
            return size > 200
        }
    }

    @Synchronized
    private fun getCachedEntry(key: String): Pair<Boolean, Map<String, String>?> {
        if (!descriptionCache.containsKey(key)) return Pair(false, null)
        return Pair(true, descriptionCache[key])
    }

    @Synchronized
    private fun putCache(key: String, value: Map<String, String>) { descriptionCache[key] = value }

    suspend fun fetchPageSignals(urlStr: String): FetchOutcome = withContext(Dispatchers.IO) {
        val cacheKey = try {
            val parsed = URL(if (urlStr.startsWith("http")) urlStr else "https://$urlStr")
            parsed.host.lowercase()
        } catch (e: Exception) {
            return@withContext FetchOutcome.FetchError("Invalid URL")
        }

        val (found, cached) = getCachedEntry(cacheKey)
        if (found) {
            return@withContext if (cached.isNullOrEmpty()) FetchOutcome.FetchEmpty else FetchOutcome.Success(cached!!)
        }

        var lastException: Exception? = null
        repeat(2) { attempt ->
            var response: okhttp3.Response? = null
            try {
                return@withContext withTimeout(8000L) {
                    val url = URL(if (urlStr.startsWith("http")) urlStr else "https://$urlStr")
                    val addr = try { InetAddress.getByName(url.host) } catch (e: Exception) { throw Exception("DNS resolution failed") }
                    if (isPrivateHost(addr)) {
                        throw Exception("Blocked: private address")
                    }
                    // SSRF protection: private IP already rejected above.
                    // Use original hostname to preserve SNI / CDN routing.
                    val request = Request.Builder()
                        .url(url.toString())
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                        .header("Range", "bytes=0-8191")
                        .build()

                    response = okHttpClient.newCall(request).execute()

                    if (!response!!.isSuccessful) {
                        throw Exception("HTTP error: ${response!!.code}")
                    } else {
                        val inputStream = response!!.body?.byteStream()
                        val reader = inputStream?.bufferedReader()
                        val charBuffer = CharArray(8192)
                        
                        var totalHtml = ""
                        if (reader != null) {
                            val charsRead1 = reader.read(charBuffer)
                            if (charsRead1 > 0) {
                                totalHtml += String(charBuffer, 0, charsRead1)
                                // read body
                                val charsRead2 = reader.read(charBuffer)
                                if (charsRead2 > 0) {
                                    totalHtml += String(charBuffer, 0, charsRead2)
                                }
                            }
                        }
                        
                        if (totalHtml.isBlank() || totalHtml.length < 200) {
                            putCache(cacheKey, emptyMap())
                            FetchOutcome.FetchEmpty
                        } else {
                            val signals = extractSignals(totalHtml)
                            putCache(cacheKey, signals)
                            FetchOutcome.Success(signals)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error fetching meta description for $urlStr")
                lastException = e
                if (attempt == 0) delay(500)
            } finally {
                response?.close()
            }
        }
        
        putCache(cacheKey, emptyMap())
        if (lastException is kotlinx.coroutines.TimeoutCancellationException) {
            FetchOutcome.FetchTimeout
        } else {
            FetchOutcome.FetchError(lastException?.message ?: "Unknown Error")
        }
    }

    private fun isPrivateHost(addr: InetAddress): Boolean {
        return addr.isLoopbackAddress ||
            addr.isSiteLocalAddress ||
            addr.isLinkLocalAddress ||
            addr.isAnyLocalAddress
    }

    fun extractSignals(html: String): Map<String, String> {
        val doc = Jsoup.parse(html)
        val head = doc.head()
        return mapOf(
            "meta_description" to (head?.select("meta[name=description]")?.attr("content").orEmpty()),
            "meta_keywords" to (head?.select("meta[name=keywords]")?.attr("content").orEmpty()),
            "og_title" to (head?.select("meta[property=og:title]")?.attr("content").orEmpty()),
            "og_description" to (head?.select("meta[property=og:description]")?.attr("content").orEmpty()),
            "twitter_description" to (head?.select("meta[name=twitter:description]")?.attr("content").orEmpty()),
            "page_title" to (head?.selectFirst("title")?.text().orEmpty()),
            "first_h1" to (doc.selectFirst("h1")?.text().orEmpty()),
            "body_text" to (doc.body()?.text()?.take(2000).orEmpty())
        ).filterValues { it.isNotBlank() }
    }
}
