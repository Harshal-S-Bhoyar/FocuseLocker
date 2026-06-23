package com.focuslock.domain.usecase

import android.content.Context
import com.focuslock.domain.model.DoHResult
import com.focuslock.data.repository.BlockingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
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
            val sharedPreferences = context.getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE)
            val profileId = sharedPreferences.getString("NEXTDNS_PROFILE_ID", com.focuslock.BuildConfig.NEXTDNS_PROFILE_ID) ?: ""
            val endpoint = if (profileId.isBlank()) {
                com.focuslock.BuildConfig.FALLBACK_DNS_ENDPOINT
            } else {
                "${com.focuslock.BuildConfig.NEXTDNS_BASE_URL}$profileId"
            }

            val request = Request.Builder()
                .url("$endpoint?name=$domain&type=A")
                .header("Accept", "application/dns-json")
                .build()
                
            // ponytail: fresh builder = no shared connection pool. newBuilder() inherits the pool
            // which lets OkHttp reuse stale connections and skip DNS entirely, falling back to
            // NetworkModule's dnsCache whose BootstrapDns has vpnService=null — causing UDP DNS
            // to be intercepted by the TUN interface = recursion loop.
            val bootstrapDns = com.focuslock.service.vpn.BootstrapDns()
            val customClient = okHttpClient.newBuilder()
                .connectionPool(okhttp3.ConnectionPool(0, 1, java.util.concurrent.TimeUnit.NANOSECONDS))
                .dns(bootstrapDns)
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.SECONDS)
                .proxy(java.net.Proxy.NO_PROXY)
                .build()

            val response = customClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext DoHResult.QueryFailed

            val responseBody = response.body?.string() ?: return@withContext DoHResult.QueryFailed
            val json = Json.parseToJsonElement(responseBody).jsonObject
            
            val status = json["Status"]?.jsonPrimitive?.intOrNull ?: -1
            val answers = json["Answer"]?.jsonArray
            
            if (status == 3) {
                // NXDOMAIN - NextDNS/AdGuard often return this for blocked domains
                return@withContext DoHResult.Blocked
            } else if (status == 0) {
                // Status 0 (NOERROR) can still be blocked if it resolves to 0.0.0.0 or :: (NextDNS default)
                var isBlocked = false
                answers?.forEach { element ->
                    val data = element.jsonObject["data"]?.jsonPrimitive?.content
                    if (data == "0.0.0.0" || data == "::") {
                        isBlocked = true
                    }
                }
                return@withContext if (isBlocked) DoHResult.Blocked else DoHResult.Clean
            } else {
                return@withContext DoHResult.Unknown
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Query failed with exception")
            DoHResult.QueryFailed
        }
    }

    suspend fun checkAndCacheDoH(url: String): DoHResult {
        val domain = DomainExtractor.extract(url) ?: return DoHResult.QueryFailed

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


}
