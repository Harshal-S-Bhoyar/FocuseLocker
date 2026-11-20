package com.focuslock.data.worker

import android.content.Context
import android.os.UserManager
import androidx.annotation.VisibleForTesting
import androidx.work.*
import com.focuslock.domain.repository.BackgroundSyncScheduler
import com.focuslock.worker.CooldownTimerWorker
import com.focuslock.worker.EmergencyBypassWorker
import com.focuslock.worker.ListSyncWorker
import com.focuslock.worker.ScheduleCheckWorker
import com.focuslock.worker.SessionExpiryWorker
import com.focuslock.worker.SessionTimerWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single WorkManager scheduling facade for session timers, unlock timers,
 * schedule checks, and list sync. Merged from SessionWorkerScheduler,
 * UnlockWorkerScheduler, and WorkManagerSyncScheduler — methods moved
 * verbatim (same work names, policies, constraints, and guards).
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : BackgroundSyncScheduler {
    var workManager: WorkManager? = null

    /**
     * Direct-boot guard. WorkManager and Room live in credential-encrypted storage, so a
     * LOCKED_BOOT_COMPLETED rearm throws before the first unlock. BOOT_COMPLETED re-arms
     * after unlock, so skipping is safe. Test seam: the platform lookup is not mockable
     * off-device.
     */
    @VisibleForTesting
    internal var isUserUnlocked: () -> Boolean = {
        context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
    }

    companion object {
        private const val SESSION_TIMER_WORK_NAME = "session_timer_work"
        private const val SESSION_EXPIRY_WORK_NAME = "session_expiry_work"
        private const val ACCESSIBILITY_HEALTH_WORK_NAME = AccessibilityHealthWorker.WORK_NAME
        private const val COOLDOWN_WORK_NAME = "cooldown_timer_work"
        private const val EMERGENCY_BYPASS_WORK_NAME = "emergency_bypass_work"
    }

    fun scheduleScheduleChecks() {
        if (!isUserUnlocked()) {
            Timber.i("WorkScheduler: user locked, deferring schedule checks to BOOT_COMPLETED.")
            return
        }
        Timber.d("WorkScheduler: Scheduling periodic schedule checks.")
        val workManager = WorkManager.getInstance(context)
        val checkWorkRequest = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        ).setConstraints(Constraints.Builder().build()).build()

        workManager.enqueueUniquePeriodicWork(
            ScheduleCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            checkWorkRequest
        )
    }

    fun scheduleSessionTimers(expiresAt: Long) {
        Timber.d("WorkScheduler: Scheduling session timers. expiresAt=$expiresAt")
        val workManager = WorkManager.getInstance(context)

        // 1. Periodic Worker (every 15 mins) to update notification and check expiry
        val constraints = Constraints.Builder().build()
        val periodicWorkRequest = PeriodicWorkRequestBuilder<SessionTimerWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            SESSION_TIMER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )

        // 2. Periodic Worker (every 15 mins) to check accessibility health
        val healthWorkRequest = PeriodicWorkRequestBuilder<AccessibilityHealthWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        workManager.enqueueUniquePeriodicWork(
            ACCESSIBILITY_HEALTH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            healthWorkRequest
        )

        // 3. Exact Expiry Worker (OneTimeWork) delayed until expiresAt
        val delayMillis = expiresAt - System.currentTimeMillis()
        Timber.d("WorkScheduler: Enqueued periodic workers. delayMillis for expiry worker=$delayMillis")
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
        Timber.d("WorkScheduler: Cancelling all session timers and workers.")
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(SESSION_TIMER_WORK_NAME)
        workManager.cancelUniqueWork(SESSION_EXPIRY_WORK_NAME)
        workManager.cancelUniqueWork(ACCESSIBILITY_HEALTH_WORK_NAME)
    }

    fun scheduleCooldownTimer(endsAt: Long) {
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

    fun scheduleEmergencyBypass(endsAt: Long) {
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

    fun cancelUnlockTimers() {
        WorkManager.getInstance(context).cancelUniqueWork(COOLDOWN_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(EMERGENCY_BYPASS_WORK_NAME)
    }

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
