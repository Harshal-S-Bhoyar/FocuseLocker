package com.focuslock.data.scheduler

import android.content.Context
import androidx.work.*
import com.focuslock.domain.repository.BackgroundSyncScheduler
import com.focuslock.worker.ListSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : BackgroundSyncScheduler {
    var workManager: WorkManager? = null

    override fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val syncRequest = PeriodicWorkRequestBuilder<ListSyncWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
            
        val wm = workManager ?: WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "stevenblack_list_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
