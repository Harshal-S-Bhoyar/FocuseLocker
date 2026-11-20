package com.focuslock.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.worker.ScheduleAlarmScheduler
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.ActivationResult
import com.focuslock.domain.usecase.ScheduleEngine
import com.focuslock.notification.SessionNotificationManager
import com.focuslock.service.overlay.PermissionEnforcementService
import com.focuslock.util.PermissionChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@HiltWorker
class ScheduleCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleEngine: ScheduleEngine,
    private val activateSessionUseCase: ActivateSessionUseCase,
    private val notificationManager: SessionNotificationManager,
    private val scheduleAlarmScheduler: ScheduleAlarmScheduler
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "schedule_check_work"
        const val LIVE_RETRY_SUPPRESS_MILLIS = 5L * 60L * 1000L

        @Volatile internal var lastLiveWinsKey: String? = null
        @Volatile internal var lastLiveWinsAtMillis: Long = 0L
        @Volatile internal var lastPermissionEnforcementKey: String? = null
        @Volatile internal var clockMillis: () -> Long = { System.currentTimeMillis() }

        internal fun resetBackoffForTest() {
            lastLiveWinsKey = null
            lastLiveWinsAtMillis = 0L
            lastPermissionEnforcementKey = null
            clockMillis = { System.currentTimeMillis() }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            runCheck(LocalDateTime.now())
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Success used to swallow every failure (DB, activation, alarm). Retry is the
            // only signal WorkManager can act on.
            Timber.e(e, "ScheduleCheckWorker: check failed, retrying")
            Result.retry()
        }
    }

    internal suspend fun runCheck(now: LocalDateTime) {
        val weekdayIso = now.dayOfWeek.value
        val minutesNow = now.hour * 60 + now.minute
        val today = now.toLocalDate()
        if (!scheduleRepository.isScheduleEnabledSync()) {
            Timber.d("ScheduleCheckWorker: schedule paused, skipping.")
            return
        }
        // Occurrence key computed before the permission gate so permission-dead
        // ticks can back off per occurrence. Enabled gate stays first.
        val windows = scheduleRepository.getWindowsSync()
        runCatching { scheduleAlarmScheduler.scheduleNext(windows, now) }
            .onFailure { Timber.e(it, "ScheduleCheckWorker: alarm reschedule failed") }
        val accessibilityOk = PermissionChecker.isAccessibilityServiceEnabled(applicationContext)
        val overlayOk = PermissionChecker.isOverlayPermissionGranted(applicationContext)
        // Clear the dedupe key on recovery, or a second loss in the same occurrence
        // never re-posts.
        if (accessibilityOk && overlayOk) lastPermissionEnforcementKey = null
        val due = scheduleEngine.dueWindow(windows, weekdayIso, minutesNow)
        if (due == null) {
            if (!accessibilityOk || !overlayOk) {
                val idleKey = "idle:$today"
                if (lastPermissionEnforcementKey == idleKey) {
                    Timber.d("ScheduleCheckWorker: enforcement already started for $idleKey, skipping.")
                    return
                }
                lastPermissionEnforcementKey = idleKey
                Timber.w("ScheduleCheckWorker: permissions lost (accessibility=$accessibilityOk overlay=$overlayOk), routing to repair; skipping activation.")
                PermissionEnforcementService.start(applicationContext)
            }
            return
        }
        val occurrenceKey = scheduleEngine.occurrenceKey(due, weekdayIso, today)
        if (!accessibilityOk || !overlayOk) {
            if (lastPermissionEnforcementKey == occurrenceKey) {
                Timber.d("ScheduleCheckWorker: enforcement already started for $occurrenceKey, skipping.")
                return
            }
            lastPermissionEnforcementKey = occurrenceKey
            Timber.w("ScheduleCheckWorker: permissions lost (accessibility=$accessibilityOk overlay=$overlayOk), routing to repair; skipping activation.")
            PermissionEnforcementService.start(applicationContext)
            return
        }
        // Exact remaining, not rounded minutes: a session started one second before the window
        // end must not outlive the boundary and block the adjacent window.
        val remainingMillis = scheduleEngine.remainingMillis(due, now)
        if (remainingMillis < ActivateSessionUseCase.MIN_DURATION_MILLIS) return
        if (scheduleRepository.getLastFiredSchedule() == occurrenceKey) {
            Timber.d("ScheduleCheckWorker: duplicate occurrence, skipping.")
            return
        }
        val nowMs = clockMillis()
        if (lastLiveWinsKey == occurrenceKey && nowMs - lastLiveWinsAtMillis < LIVE_RETRY_SUPPRESS_MILLIS) {
            Timber.d("ScheduleCheckWorker: live-session retry suppressed within window.")
            return
        }
        when (val result = activateSessionUseCase(remainingMillis)) {
            is ActivationResult.Activated -> {
                if (lastLiveWinsKey == occurrenceKey) {
                    lastLiveWinsKey = null
                    lastLiveWinsAtMillis = 0L
                }
                scheduleRepository.setLastFiredSchedule(occurrenceKey)
                notificationManager.notifyScheduleFired(formatRule(due.weekday, due.startMinutes))
            }
            is ActivationResult.AlreadyActive -> {
                // A live session covers this occurrence: back off this key, keep the notice silent.
                lastLiveWinsKey = occurrenceKey
                lastLiveWinsAtMillis = nowMs
                Timber.d("ScheduleCheckWorker: activation skipped, a session is already active.")
            }
            is ActivationResult.Failed -> {
                Timber.e(result.error, "ScheduleCheckWorker: activation skipped.")
            }
        }
    }

    private fun formatRule(weekday: Int, startMinutes: Int): String {
        val day = DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$day ${"%02d".format(startMinutes / 60)}:${"%02d".format(startMinutes % 60)}"
    }
}
