package com.focuslock.data.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class UnlockWorkerSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: UnlockWorkerScheduler

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        mockkStatic(WorkManager::class)
        try { mockkObject(WorkManager) } catch (e: Exception) {}
        every { WorkManager.getInstance(any()) } returns workManager
        
        scheduler = UnlockWorkerScheduler(context)
    }

    @After
    fun teardown() {
        unmockkStatic(WorkManager::class)
        try { unmockkObject(WorkManager) } catch (e: Exception) {}
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
}
