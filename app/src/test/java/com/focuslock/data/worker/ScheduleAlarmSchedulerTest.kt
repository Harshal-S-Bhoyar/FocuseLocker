package com.focuslock.data.worker

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.domain.usecase.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScheduleAlarmSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val engine = ScheduleEngine()
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val scheduler = ScheduleAlarmScheduler(context, engine)

    private val mondayWindow = ScheduleWindowEntity(weekday = 1, startMinutes = 1200, endMinutes = 840)
    private val mondayNoon = LocalDateTime.of(2026, 10, 12, 12, 0)

    @Test
    fun `an alarm is set for the exact next window start`() {
        val expected = engine.nextStartEpochMillis(listOf(mondayWindow), mondayNoon)

        scheduler.scheduleNext(listOf(mondayWindow), mondayNoon)

        val alarm = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(expected, alarm.triggerAtTime)
    }

    @Test
    fun `an overnight window is armed in the evening, not after midnight`() {
        val expected = LocalDateTime.of(2026, 10, 12, 20, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        scheduler.scheduleNext(listOf(mondayWindow), mondayNoon)

        assertEquals(expected, shadowOf(alarmManager).nextScheduledAlarm.triggerAtTime)
    }

    @Test
    fun `no windows cancels the pending alarm`() {
        scheduler.scheduleNext(listOf(mondayWindow), mondayNoon)
        assertNotNull(shadowOf(alarmManager).peekNextScheduledAlarm())

        scheduler.scheduleNext(emptyList(), mondayNoon)

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }
}
