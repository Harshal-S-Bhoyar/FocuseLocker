package com.focuslock.domain.usecase

import android.content.Context
import com.focuslock.domain.model.DoHResult
import com.focuslock.domain.repository.BlockingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoHQueryUseCase @Inject constructor(
    private val blockingRepository: BlockingRepository,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun queryDoH(domain: String): DoHResult = withContext(Dispatchers.IO) {
        try {
            val profileId = com.focuslock.BuildConfig.NEXTDNS_PROFILE_ID
            val endpoint = if (profileId.isBlank()) {
                "https://family.adguard-dns.com/dns-query"
            } else {
                "https://$profileId.dns.nextdns.io/dns-query"
            }

            val request = Request.Builder()
                .url("$endpoint?name=$domain&type=A")
                .header("Accept", "application/dns-json")
                .build()
                
            val customClient = okHttpClient.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()

            val response = customClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext DoHResult.QueryFailed

            val responseBody = response.body?.string() ?: return@withContext DoHResult.QueryFailed
            val json = Json.parseToJsonElement(responseBody).jsonObject
            
            val status = json["Status"]?.jsonPrimitive?.intOrNull ?: -1
            when (status) {
                0 -> DoHResult.Clean
                3 -> DoHResult.Blocked
                else -> DoHResult.Unknown
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DoHResult.QueryFailed
        }
    }

    suspend fun checkAndCacheDoH(url: String): DoHResult {
        val domain = extractDomain(url) ?: return DoHResult.QueryFailed

        val cachedStatus = blockingRepository.getCachedDomainStatus(domain)
        if (cachedStatus == "PERMANENT") return DoHResult.Blocked
        if (cachedStatus == "CLEAN_CACHE") return DoHResult.Clean

        val result = queryDoH(domain)
        when (result) {
            is DoHResult.Blocked -> {
                blockingRepository.addAutoBlockedDomain(domain, "", "PERMANENT", null)
            }
            is DoHResult.Clean -> {
                val expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
                blockingRepository.addAutoBlockedDomain(domain, "", "CLEAN_CACHE", expiresAt)
            }
            is DoHResult.Unknown, is DoHResult.QueryFailed -> {
                // Do not cache
            }
        }
        return result
    }

    private fun extractDomain(url: String): String? {
        return try {
            val uri = java.net.URI(if (url.startsWith("http")) url else "https://$url")
            var host = uri.host ?: return null
            host = host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
            host
        } catch (e: Exception) {
            null
        }
    }
}
