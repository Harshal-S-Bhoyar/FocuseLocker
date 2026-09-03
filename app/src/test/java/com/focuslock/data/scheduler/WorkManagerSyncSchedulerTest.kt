package com.focuslock.data.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkManagerSyncSchedulerTest {

    private lateinit var scheduler: WorkManagerSyncScheduler
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        
        scheduler = WorkManagerSyncScheduler(context)
        scheduler.workManager = workManager
    }

    @After
    fun tearDown() {
        try {
            unmockkStatic(WorkManager::class)
        } catch (_: Exception) {
            // Static mocking is only enabled by the fallback-manager scenario.
        }
        try {
            unmockkObject(WorkManager)
        } catch (_: Exception) {
            // Object mocking is only enabled by the fallback-manager scenario.
        }
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
        mockkStatic(WorkManager::class)
        try {
            mockkObject(WorkManager)
        } catch (_: Exception) {
            // Java static mocking may already expose the method as an object.
        }
        every { WorkManager.getInstance(any()) } returns workManager
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
        val requestSlot = io.mockk.slot<PeriodicWorkRequest>()
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
