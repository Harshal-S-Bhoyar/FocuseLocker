package com.focuslock.data.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
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
        
        mockkStatic(WorkManager::class)
        try { mockkObject(WorkManager) } catch (e: Exception) {}
        every { WorkManager.getInstance(any()) } returns workManager

        scheduler = WorkManagerSyncScheduler(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(WorkManager::class)
        try { unmockkObject(WorkManager) } catch (e: Exception) {}
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
}
