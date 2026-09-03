package com.focuslock.data.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
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

class SessionWorkerSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SessionWorkerScheduler

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        mockkStatic(WorkManager::class)
        try { mockkObject(WorkManager) } catch (e: Exception) {}
        every { WorkManager.getInstance(any()) } returns workManager
        
        scheduler = SessionWorkerScheduler(context)
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
}
