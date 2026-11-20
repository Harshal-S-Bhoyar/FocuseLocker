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

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScheduleBootRearmTest {

    private lateinit var context: Context
    private lateinit var bootReceiver: BootReceiver
    private lateinit var workScheduler: WorkScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val sessionRepository = mockk<SessionRepository>(relaxed = true)
        workScheduler = mockk(relaxed = true)
        val sessionRuntimeController = mockk<SessionRuntimeController>(relaxed = true)

        bootReceiver = spyk(BootReceiver()).apply {
            this.sessionRepository = sessionRepository
            this.workScheduler = this@ScheduleBootRearmTest.workScheduler
            this.sessionRuntimeController = sessionRuntimeController
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
    fun `boot re-arms schedule checks even with no live session`() = runTest {
        bootReceiver.ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        coEvery { bootReceiver.sessionRepository.getActiveSessionSync() } returns null
        every { PermissionChecker.isAccessibilityServiceEnabled(any<Context>()) } returns true
        every { PermissionChecker.isOverlayPermissionGranted(any<Context>()) } returns true

        bootReceiver.processBootIntent(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        advanceUntilIdle()

        verify { workScheduler.scheduleScheduleChecks() }
    }

    @Test
    fun `locked-boot quickboot and replace intents rearm schedule checks`() {
        val actions = listOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        actions.forEach { action ->
            bootReceiver.processBootIntent(context, Intent(action))
        }

        verify(exactly = 3) { workScheduler.scheduleScheduleChecks() }
    }

    @Test
    fun `unknown intent never rearms`() {
        bootReceiver.processBootIntent(context, Intent("android.intent.action.SOME_OTHER"))

        verify(exactly = 0) { workScheduler.scheduleScheduleChecks() }
    }
}
