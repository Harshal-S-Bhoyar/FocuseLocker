package com.focuslock.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkSchedulerTest {

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
        scheduler.workManager = workManager
    }

    @Test
    fun `scheduleScheduleChecks defers while the user is still locked`() {
        scheduler.isUserUnlocked = { false }

        scheduler.scheduleScheduleChecks()

        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(
                any<String>(),
                any<ExistingPeriodicWorkPolicy>(),
                any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun `scheduleScheduleChecks enqueues the backstop once unlocked`() {
        scheduler.isUserUnlocked = { true }

        scheduler.scheduleScheduleChecks()

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                ScheduleCheckWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any<PeriodicWorkRequest>()
            )
        }
    }

    @After
    fun teardown() {
        unmockkStatic(WorkManager::class)
        try { unmockkObject(WorkManager) } catch (e: Exception) {}
    }

    @Test
    fun `scheduleSessionTimers enqueues periodic and one time work`() {
        val futureTime = System.currentTimeMillis() + 60000L // 1 minute from now

        scheduler.scheduleSessionTimers(futureTime)

        verify {
            workManager.enqueueUniquePeriodicWork(
                "session_timer_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                (any() as androidx.work.PeriodicWorkRequest)
            )
        }

        verify {
            workManager.enqueueUniquePeriodicWork(
                AccessibilityHealthWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                (any() as androidx.work.PeriodicWorkRequest)
            )
        }

        verify {
            workManager.enqueueUniqueWork(
                "session_expiry_work",
                ExistingWorkPolicy.REPLACE,
                (any() as androidx.work.OneTimeWorkRequest)
            )
        }
    }

    @Test
    fun `scheduleSessionTimers skips one time work if expiresAt is in past`() {
        val pastTime = System.currentTimeMillis() - 60000L // 1 minute ago

        scheduler.scheduleSessionTimers(pastTime)

        verify {
            workManager.enqueueUniquePeriodicWork(
                "session_timer_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                (any() as androidx.work.PeriodicWorkRequest)
            )
        }

        verify {
            workManager.enqueueUniquePeriodicWork(
                AccessibilityHealthWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                (any() as androidx.work.PeriodicWorkRequest)
            )
        }

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                "session_expiry_work",
                (any() as androidx.work.ExistingWorkPolicy),
                (any() as androidx.work.OneTimeWorkRequest)
            )
        }
    }

    @Test
    fun `cancelSessionTimers cancels all works`() {
        scheduler.cancelSessionTimers()

        verify { workManager.cancelUniqueWork("session_timer_work") }
        verify { workManager.cancelUniqueWork("session_expiry_work") }
        verify { workManager.cancelUniqueWork(AccessibilityHealthWorker.WORK_NAME) }
    }

    @Test
    fun `scheduleSessionTimers skips expiry work at the current-time boundary`() {
        scheduler.scheduleSessionTimers(System.currentTimeMillis())

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                "session_timer_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                (any() as androidx.work.PeriodicWorkRequest)
            )
        }
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                "session_expiry_work",
                any<ExistingWorkPolicy>(),
                any<androidx.work.OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `scheduleSessionTimers replaces work when called repeatedly`() {
        val futureTime = System.currentTimeMillis() + 60_000L

        scheduler.scheduleSessionTimers(futureTime)
        scheduler.scheduleSessionTimers(futureTime)

        verify(exactly = 2) {
            workManager.enqueueUniquePeriodicWork(
                "session_timer_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                (any() as androidx.work.PeriodicWorkRequest)
            )
        }
        verify(exactly = 2) {
            workManager.enqueueUniqueWork(
                "session_expiry_work",
                ExistingWorkPolicy.REPLACE,
                (any() as androidx.work.OneTimeWorkRequest)
            )
        }
    }

    @Test
    fun `scheduleCooldownTimer enqueues work if time is future`() {
        val futureTime = System.currentTimeMillis() + 60000L // 1 minute from now

        scheduler.scheduleCooldownTimer(futureTime)

        verify {
            workManager.enqueueUniqueWork(
                "cooldown_timer_work",
                ExistingWorkPolicy.REPLACE,
                (any() as androidx.work.OneTimeWorkRequest)
            )
        }
    }

    @Test
    fun `scheduleCooldownTimer skips work if time is past`() {
        val pastTime = System.currentTimeMillis() - 60000L

        scheduler.scheduleCooldownTimer(pastTime)

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                "cooldown_timer_work",
                (any() as androidx.work.ExistingWorkPolicy),
                (any() as androidx.work.OneTimeWorkRequest)
            )
        }
    }

    @Test
    fun `scheduleEmergencyBypass enqueues work if time is future`() {
        val futureTime = System.currentTimeMillis() + 60000L

        scheduler.scheduleEmergencyBypass(futureTime)

        verify {
            workManager.enqueueUniqueWork(
                "emergency_bypass_work",
                ExistingWorkPolicy.REPLACE,
                (any() as androidx.work.OneTimeWorkRequest)
            )
        }
    }

    @Test
    fun `scheduleEmergencyBypass skips work if time is past`() {
        val pastTime = System.currentTimeMillis() - 60000L

        scheduler.scheduleEmergencyBypass(pastTime)

        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                "emergency_bypass_work",
                (any() as androidx.work.ExistingWorkPolicy),
                (any() as androidx.work.OneTimeWorkRequest)
            )
        }
    }

    @Test
    fun `cancelUnlockTimers cancels both works`() {
        scheduler.cancelUnlockTimers()

        verify { workManager.cancelUniqueWork("cooldown_timer_work") }
        verify { workManager.cancelUniqueWork("emergency_bypass_work") }
    }

    @Test
    fun `schedulePeriodicSync enqueues unique periodic work`() {
        scheduler.schedulePeriodicSync()

        verify {
            workManager.enqueueUniquePeriodicWork(
                "stevenblack_list_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun `schedulePeriodicSync uses the injected WorkManager instead of resolving another instance`() {
        scheduler.schedulePeriodicSync()

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                "stevenblack_list_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun `schedulePeriodicSync resolves WorkManager from context when no manager is injected`() {
        scheduler.workManager = null

        scheduler.schedulePeriodicSync()

        verify(exactly = 1) {
            WorkManager.getInstance(context)
        }
        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                "stevenblack_list_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun `schedulePeriodicSync configures unmetered network and battery constraint`() {
        val requestSlot = slot<PeriodicWorkRequest>()
        scheduler.schedulePeriodicSync()

        verify {
            workManager.enqueueUniquePeriodicWork(
                "stevenblack_list_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                capture(requestSlot)
            )
        }

        val constraints = requestSlot.captured.workSpec.constraints
        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `schedulePeriodicSync keeps the existing unique work across repeated calls`() {
        scheduler.schedulePeriodicSync()
        scheduler.schedulePeriodicSync()

        verify(exactly = 2) {
            workManager.enqueueUniquePeriodicWork(
                "stevenblack_list_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                any<PeriodicWorkRequest>()
            )
        }
    }
}
