package com.focuslock.data.worker

import android.content.Context
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.focuslock.worker.ScheduleCheckWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class WorkerFlexTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkScheduler

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        mockkStatic(WorkManager::class)
        try { mockkObject(WorkManager) } catch (e: Exception) {}
        every { WorkManager.getInstance(any()) } returns workManager
        scheduler = WorkScheduler(context)
        scheduler.isUserUnlocked = { true }
    }

    @After
    fun teardown() {
        unmockkStatic(WorkManager::class)
        try { unmockkObject(WorkManager) } catch (e: Exception) {}
    }

    @Test
    fun `schedule checks carry interval plus flex`() {
        val slot = slot<PeriodicWorkRequest>()
        scheduler.scheduleScheduleChecks()
        verify {
            workManager.enqueueUniquePeriodicWork(
                ScheduleCheckWorker.WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                capture(slot)
            )
        }
        assertEquals(TimeUnit.MINUTES.toMillis(15), slot.captured.workSpec.intervalDuration)
        assertEquals(TimeUnit.MINUTES.toMillis(5), slot.captured.workSpec.flexDuration)
    }

    @Test
    fun `session timers carry interval plus flex`() {
        val slots = mutableListOf<PeriodicWorkRequest>()
        scheduler.scheduleSessionTimers(System.currentTimeMillis() + 60_000L)
        verify(exactly = 2) {
            workManager.enqueueUniquePeriodicWork(
                any(),
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                capture(slots)
            )
        }
        assertEquals(TimeUnit.MINUTES.toMillis(15), slots.last().workSpec.intervalDuration)
        assertEquals(TimeUnit.MINUTES.toMillis(5), slots.last().workSpec.flexDuration)
    }
}
