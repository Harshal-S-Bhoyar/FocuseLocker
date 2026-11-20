package com.focuslock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.worker.ScheduleAlarmScheduler
import com.focuslock.worker.ScheduleCheckWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Fires at a window start. Enqueues an expedited check (WorkManager quota permitting) and
 * re-arms the next alarm. The periodic worker remains the backstop.
 */
@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WINDOW_START = "com.focuslock.action.SCHEDULE_WINDOW_START"
        private const val ALARM_CHECK_WORK = "schedule_alarm_check"
        private const val TAG = "RCV_SCHEDULE_ALARM"
    }

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var scheduleAlarmScheduler: ScheduleAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WINDOW_START) return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // Do not enqueue or keep the alarm chain alive for a paused schedule; the next
                // arming re-enables it and schedules the next start.
                if (!scheduleRepository.isScheduleEnabledSync()) {
                    scheduleAlarmScheduler.cancel()
                    return@launch
                }
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ALARM_CHECK_WORK,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ScheduleCheckWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                )
                scheduleAlarmScheduler.scheduleNext(scheduleRepository.getWindowsSync())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Alarm check failed")
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
