package com.focuslock.data.repository

import com.focuslock.domain.model.DoHResult
import com.focuslock.domain.repository.DoHRepository
import com.focuslock.domain.repository.ConfigRepository
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
class DoHRepositoryImpl @Inject constructor(
    private val configRepository: ConfigRepository,
    private val okHttpClient: OkHttpClient
) : DoHRepository {

    override suspend fun queryDoH(domain: String): DoHResult = withContext(Dispatchers.IO) {
        try {
            val profileId = configRepository.getNextDnsProfileId()
            val endpoint = if (profileId.isBlank()) {
                com.focuslock.BuildConfig.FALLBACK_DNS_ENDPOINT
            } else {
                "${com.focuslock.BuildConfig.NEXTDNS_BASE_URL}$profileId"
            }

            val request = Request.Builder()
                .url("$endpoint?name=$domain&type=A")
                .header("Accept", "application/dns-json")
                .build()

            val responseBody = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext DoHResult.QueryFailed
                response.body?.string() ?: return@withContext DoHResult.QueryFailed
            }
            val json = Json.parseToJsonElement(responseBody).jsonObject
            
            val status = json["Status"]?.jsonPrimitive?.intOrNull ?: -1
            val answers = json["Answer"]?.jsonArray
            
            if (status == 3) {
                return@withContext DoHResult.Blocked
            } else if (status == 0) {
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
}
