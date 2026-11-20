package com.focuslock.data.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.domain.usecase.ScheduleEngine
import com.focuslock.receiver.ScheduleAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Exact alarm at the next window start. WorkManager's 15-minute periodic work is the
 * backstop, but Doze can defer it for hours and a short window would never fire.
 */
@Singleton
class ScheduleAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleEngine: ScheduleEngine
) {
    fun scheduleNext(windows: List<ScheduleWindowEntity>, now: LocalDateTime = LocalDateTime.now()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent()
        val triggerAt = scheduleEngine.nextStartEpochMillis(windows, now)
        if (triggerAt == null) {
            alarmManager.cancel(pendingIntent)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // ponytail: no exact-alarm grant (Android 14+ default). Inexact still beats
                // waiting for the next periodic work run.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Timber.d("ScheduleAlarmScheduler: next window start in %d ms", triggerAt - System.currentTimeMillis())
        } catch (e: SecurityException) {
            // Revoked between the capability check and the set call.
            Timber.w(e, "ScheduleAlarmScheduler: exact alarm rejected, relying on periodic work")
        }
    }

    fun cancel() {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ScheduleAlarmReceiver::class.java).setAction(ScheduleAlarmReceiver.ACTION_WINDOW_START),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
