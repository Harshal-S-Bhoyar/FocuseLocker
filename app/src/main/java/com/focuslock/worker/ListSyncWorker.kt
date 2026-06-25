package com.focuslock.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focuslock.data.local.dao.BlockedDomainDao
import com.focuslock.data.parser.StevenBlackConfig
import com.focuslock.data.parser.StevenBlackParser
import com.focuslock.data.repository.BlockingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class ListSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val blockingRepository: BlockingRepository,
    private val blockedDomainDao: BlockedDomainDao,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            timber.log.Timber.i("ListSyncWorker started.")
            
            // Garbage collect expired auto-blocked domains in the background
            blockedDomainDao.deleteExpiredEntries()

            val activeCategories = blockingRepository.getActiveCategoryKeys()
            
            val sharedPreferences = context.getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            
            val customClient = okHttpClient.newBuilder()
                .apply {
                    interceptors().removeAll { it is okhttp3.logging.HttpLoggingInterceptor }
                }
                .build()

            for (categoryKey in activeCategories) {
                val url = StevenBlackConfig.categoryToUrlMap[categoryKey] ?: continue
                
                val lastETag = sharedPreferences.getString("etag_$categoryKey", null)
                
                val requestBuilder = Request.Builder().url(url)
                if (lastETag != null) {
                    requestBuilder.header("If-None-Match", lastETag)
                }

                customClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 304) {
                        return@use // Not modified, skip
                    }

                    if (response.isSuccessful) {
                        val newETag = response.header("ETag")
                        if (newETag != null) {
                            editor.putString("etag_$categoryKey", newETag)
                        }

                        response.body?.byteStream()?.let { inputStream ->
                            val entities = StevenBlackParser.parseHostsFile(inputStream, categoryKey)
                            // Insert in chunks to avoid SQLite transaction too large exception
                            entities.chunked(1000).forEach { batch ->
                                blockedDomainDao.insertDomains(batch)
                            }
                        }
                    } else {
                        return@withContext Result.retry()
                    }
                }
            }
            
            // Save last sync timestamp
            editor.putLong("last_stevenblack_sync", System.currentTimeMillis()).apply()
            
            timber.log.Timber.i("ListSyncWorker completed successfully.")
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // WorkManager cancelled us — don't retry, don't log as error
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error syncing list")
            Result.retry()
        }
    }
}
