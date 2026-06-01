package com.focuslock.data.worker

import android.content.Context
import androidx.work.*
import com.focuslock.domain.worker.UnlockWorkerScheduler
import com.focuslock.worker.CooldownTimerWorker
import com.focuslock.worker.EmergencyBypassWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnlockWorkerSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UnlockWorkerScheduler {

    companion object {
        private const val COOLDOWN_WORK_NAME = "cooldown_timer_work"
        private const val EMERGENCY_BYPASS_WORK_NAME = "emergency_bypass_work"
    }

    override fun scheduleCooldownTimer(endsAt: Long) {
        val delayMillis = endsAt - System.currentTimeMillis()
        if (delayMillis > 0) {
            val workRequest = OneTimeWorkRequestBuilder<CooldownTimerWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                COOLDOWN_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override fun scheduleEmergencyBypass(endsAt: Long) {
        val delayMillis = endsAt - System.currentTimeMillis()
        if (delayMillis > 0) {
            val workRequest = OneTimeWorkRequestBuilder<EmergencyBypassWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                EMERGENCY_BYPASS_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override fun cancelUnlockTimers() {
        WorkManager.getInstance(context).cancelUniqueWork(COOLDOWN_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(EMERGENCY_BYPASS_WORK_NAME)
    }
}
