package com.focuslock.data.repository

import com.focuslock.domain.model.FetchOutcome
import com.focuslock.domain.repository.PageSignalFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageSignalFetcherImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : PageSignalFetcher {

    // Cache: domain → signals (or empty string if none found / failed)
    // Prevents repeat network fetches for the same domain within a session.
    private val descriptionCache = com.focuslock.domain.util.SimpleLruCache<String, Map<String, String>>(200)

    private fun getCachedEntry(key: String): Map<String, String>? = descriptionCache.get(key)

    private fun putCache(key: String, value: Map<String, String>) { descriptionCache.put(key, value) }

    override suspend fun fetchPageSignals(urlStr: String): FetchOutcome = withContext(Dispatchers.IO) {
        val cacheKey = ensureHttpScheme(urlStr).toHttpUrlOrNull()?.newBuilder()?.fragment(null)?.build()?.toString()
            ?: return@withContext FetchOutcome.FetchError("Invalid URL")

        val cached = getCachedEntry(cacheKey)
        if (cached != null) {
            return@withContext if (cached.isEmpty()) FetchOutcome.FetchEmpty else FetchOutcome.Success(cached)
        }

        var lastException: Exception? = null
        repeat(2) { attempt ->
            var response: okhttp3.Response? = null
            try {
                return@withContext withTimeout(8000L) {
                    val url = URL(ensureHttpScheme(urlStr))
                    val addr = try { InetAddress.getByName(url.host) } catch (e: Exception) { throw Exception("DNS resolution failed") }
                    if (isPrivateHost(addr)) {
                        throw Exception("Blocked: private address")
                    }
                    
                    val safeClient = okHttpClient.newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .dns(object : okhttp3.Dns {
                            override fun lookup(hostname: String): List<InetAddress> {
                                if (hostname == url.host) return listOf(addr)
                                throw java.net.UnknownHostException("SSRF blocked host: $hostname")
                            }
                        })
                        .build()

                    val request = Request.Builder()
                        .url(url.toString())
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                        .header("Range", "bytes=0-8191")
                        .build()

                    response = safeClient.newCall(request).execute()

                    if (!response!!.isSuccessful) {
                        throw Exception("HTTP error: ${response!!.code}")
                    } else {
                        val inputStream = response!!.body?.byteStream()
                        val reader = inputStream?.bufferedReader()
                        val charBuffer = CharArray(8192)
                        
                        val totalHtml = StringBuilder(8192)
                        val maxChars = charBuffer.size * 2
                        if (reader != null) {
                            while (totalHtml.length < maxChars) {
                                val n = reader.read(charBuffer)
                                if (n <= 0) break
                                totalHtml.append(charBuffer, 0, n)
                            }
                        }
                        
                        if (totalHtml.isBlank() || totalHtml.length < 200) {
                            putCache(cacheKey, emptyMap())
                            FetchOutcome.FetchEmpty
                        } else {
                            val signals = extractSignals(totalHtml.toString())
                            putCache(cacheKey, signals)
                            FetchOutcome.Success(signals)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastException = e
                if (attempt == 0) delay(500)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error fetching meta description")
                lastException = e
                if (attempt == 0) delay(500)
            } finally {
                response?.close()
            }
        }
        
        if (lastException is kotlinx.coroutines.TimeoutCancellationException) {
            FetchOutcome.FetchTimeout
        } else {
            FetchOutcome.FetchError(lastException?.message ?: "Unknown Error")
        }
    }

    private fun ensureHttpScheme(urlStr: String): String {
        return if (urlStr.startsWith("http://", ignoreCase = true) || urlStr.startsWith("https://", ignoreCase = true)) {
            urlStr
        } else {
            "https://$urlStr"
        }
    }

    private fun isPrivateHost(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress || addr.isMulticastAddress) {
            return true
        }
        var address = addr.address
        if (address.size == 16) {
            if ((address[0].toInt() and 0xFE) == 0xFC) return true
            if ((0..9).all { address[it] == 0.toByte() } && address[10] == (-1).toByte() && address[11] == (-1).toByte()) {
                address = address.copyOfRange(12, 16)
            }
        }
        if (address.size == 4) {
            val b0 = address[0].toInt() and 0xFF
            val b1 = address[1].toInt() and 0xFF
            val b2 = address[2].toInt() and 0xFF
            return b0 == 0 || b0 == 127 || b0 >= 240 ||
                (b0 == 100 && b1 in 64..127) ||
                (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2)) ||
                (b0 == 198 && b1 in 18..19) ||
                (b0 == 198 && b1 == 51 && b2 == 100) ||
                (b0 == 203 && b1 == 0 && b2 == 113)
        }
        return false
    }

    private fun extractSignals(html: String): Map<String, String> {
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
