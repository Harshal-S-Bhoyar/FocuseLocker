package com.focuslock.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.WorkScheduler
import com.focuslock.util.PermissionChecker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BootGuardTest {

    private lateinit var context: Context
    private lateinit var bootReceiver: BootReceiver
    private lateinit var sessionRepository: SessionRepository
    private lateinit var workScheduler: WorkScheduler
    private lateinit var sessionRuntimeController: SessionRuntimeController

    @Before
    fun setup() {
        Timber.plant(Timber.DebugTree())
        context = ApplicationProvider.getApplicationContext()
        sessionRepository = mockk(relaxed = true)
        workScheduler = mockk(relaxed = true)
        sessionRuntimeController = mockk(relaxed = true)

        bootReceiver = spyk(BootReceiver()).apply {
            this.sessionRepository = this@BootGuardTest.sessionRepository
            this.workScheduler = this@BootGuardTest.workScheduler
            this.sessionRuntimeController = this@BootGuardTest.sessionRuntimeController
            this.ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        }

        val dummyPendingResult = mockk<android.content.BroadcastReceiver.PendingResult>(relaxed = true)
        every { bootReceiver.goAsync() } returns dummyPendingResult

        mockkObject(PermissionChecker)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `boot without session skips watchdog but still rearms schedule checks`() = runTest {
        bootReceiver.ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        coEvery { sessionRepository.getActiveSessionSync() } returns null

        bootReceiver.processBootIntent(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        advanceUntilIdle()

        verify { workScheduler.scheduleScheduleChecks() }

        val shadowApp = org.robolectric.Shadows.shadowOf(context as android.app.Application)
        val started = mutableListOf<Intent>()
        var s = shadowApp.nextStartedService
        while (s != null) {
            started.add(s)
            s = shadowApp.nextStartedService
        }
        org.junit.Assert.assertTrue(
            "Watchdog must not start without a session",
            started.none { it.component?.className == "com.focuslock.service.watchdog.PermissionWatchdogService" }
        )
    }
}
