package com.focuslock.data.worker

import android.content.Context
import androidx.work.*
import com.focuslock.worker.SessionExpiryWorker
import com.focuslock.worker.SessionTimerWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionWorkerScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val SESSION_TIMER_WORK_NAME = "session_timer_work"
        private const val SESSION_EXPIRY_WORK_NAME = "session_expiry_work"
    }

    fun scheduleSessionTimers(expiresAt: Long) {
        val workManager = WorkManager.getInstance(context)

        // 1. Periodic Worker (every 15 mins) to update notification and check expiry
        val constraints = Constraints.Builder().build()
        val periodicWorkRequest = PeriodicWorkRequestBuilder<SessionTimerWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            SESSION_TIMER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )

        // 2. Exact Expiry Worker (OneTimeWork) delayed until expiresAt
        val delayMillis = expiresAt - System.currentTimeMillis()
        if (delayMillis > 0) {
            val expiryWorkRequest = OneTimeWorkRequestBuilder<SessionExpiryWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniqueWork(
                SESSION_EXPIRY_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                expiryWorkRequest
            )
        }
    }

    fun cancelSessionTimers() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(SESSION_TIMER_WORK_NAME)
        workManager.cancelUniqueWork(SESSION_EXPIRY_WORK_NAME)
    }
}
