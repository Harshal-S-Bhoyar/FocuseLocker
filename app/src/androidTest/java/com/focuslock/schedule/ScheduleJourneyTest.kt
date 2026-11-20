package com.focuslock.schedule

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.focuslock.data.apps.AppBlockSelectionStore
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.BlockingRepository
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.ScheduleAlarmScheduler
import com.focuslock.data.worker.WorkScheduler
import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.session.SessionEnforcementStateStore
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.CleanupStaleSessionUseCase
import com.focuslock.domain.usecase.DeactivateSessionUseCase
import com.focuslock.domain.usecase.ScheduleEngine
import com.focuslock.notification.SessionNotificationManager
import com.focuslock.service.watchdog.PermissionWatchdogService
import com.focuslock.util.PermissionChecker
import com.focuslock.worker.ScheduleCheckWorker
import com.focuslock.worker.SessionExpiryWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Device journey: a due weekly window fires a lock, posts the scheduled-start
 * notice, and expiry completes it exactly like a manual session.
 *
 * Runs entirely on real objects (real Room in-memory database, real WorkManager
 * worker parameters, real notifications, real runtime controller). The only
 * hand-written double is the enforcement-state store, an interface, recorded
 * in memory. No mocking framework runs on the device.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleJourneyTest {

    private class RecordingEnforcementStateStore : SessionEnforcementStateStore {
        var activeSession: Boolean = false
        private var breachCount: Int = 0
        private var penaltyEnd: Long = 0L

        override fun isSessionActive(): Boolean = activeSession
        override fun setSessionActive(isActive: Boolean) {
            activeSession = isActive
        }

        override fun getBreachCount(): Int = breachCount
        override fun incrementBreachCount() {
            breachCount++
        }

        override fun resetBreachCount() {
            breachCount = 0
        }

        override fun getPenaltyEndTime(): Long = penaltyEnd
        override fun setPenaltyEndTime(timeMillis: Long) {
            penaltyEnd = timeMillis
        }
    }

    private lateinit var context: Context
    private lateinit var database: FocusDatabase
    private lateinit var sessionRepository: SessionRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var enforcementStore: RecordingEnforcementStateStore
    private lateinit var runtimeController: SessionRuntimeController
    private lateinit var scheduler: WorkScheduler
    private lateinit var activateSessionUseCase: ActivateSessionUseCase
    private lateinit var deactivateSessionUseCase: DeactivateSessionUseCase
    private lateinit var notificationManager: SessionNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            .close()

        database = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java)
            .allowMainThreadQueries().build()
        sessionRepository = SessionRepository(database.sessionDao())
        scheduleRepository = ScheduleRepository(database.scheduleDao(), database.appSettingDao())
        enforcementStore = RecordingEnforcementStateStore()
        notificationManager = SessionNotificationManager(context)
        runtimeController = SessionRuntimeController(context, notificationManager, enforcementStore)
        scheduler = WorkScheduler(context)
        val blockingRepository = BlockingRepository(
            database.blockedKeywordDao(),
            database.blockedCategoryDao(),
            database.blockedDomainDao(),
            context
        )
        val appBlockSelectionStore = AppBlockSelectionStore()
        deactivateSessionUseCase = DeactivateSessionUseCase(sessionRepository, scheduler, runtimeController)
        val cleanup = CleanupStaleSessionUseCase(sessionRepository, deactivateSessionUseCase)
        activateSessionUseCase = ActivateSessionUseCase(
            sessionRepository,
            scheduler,
            runtimeController,
            cleanup,
            blockingRepository,
            appBlockSelectionStore
        )
    }

    @After
    fun teardown() = runBlocking<Unit> {
        sessionRepository.getActiveSessionSync()?.let {
            deactivateSessionUseCase(it.id, SessionStatus.CANCELLED)
        }
        scheduler.cancelSessionTimers()
        PermissionWatchdogService.stop(context)
        database.close()
    }

    private fun buildScheduleCheckWorker(): ScheduleCheckWorker =
        TestListenableWorkerBuilder<ScheduleCheckWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = ScheduleCheckWorker(
                    appContext,
                    workerParameters,
                    scheduleRepository,
                    ScheduleEngine(),
                    activateSessionUseCase,
                    notificationManager,
                    ScheduleAlarmScheduler(appContext, ScheduleEngine())
                )
            })
            .build()

    private fun buildExpiryWorker(): SessionExpiryWorker =
        TestListenableWorkerBuilder<SessionExpiryWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = SessionExpiryWorker(
                    appContext,
                    workerParameters,
                    sessionRepository,
                    deactivateSessionUseCase,
                    notificationManager
                )
            })
            .build()

    @Test
    fun scheduledWindowFiresLocksNotifiesAndCompletesLikeManual() = runBlocking {
        val now = LocalDateTime.now()
        val minutesNow = now.hour * 60 + now.minute
        val startMinutes = maxOf(0, minutesNow - 5)
        val endMinutes = minOf(1439, minutesNow + 55)
        Assume.assumeTrue("needs a usable window away from midnight", minutesNow in startMinutes..<endMinutes)

        scheduleRepository.upsertWindow(
            ScheduleWindowEntity(weekday = now.dayOfWeek.value, startMinutes = startMinutes, endMinutes = endMinutes)
        )
        scheduleRepository.setScheduleEnabled(true)

        Assume.assumeTrue(
            "needs accessibility service for schedule fire",
            PermissionChecker.isAccessibilityServiceEnabled(context)
        )
        Assume.assumeTrue(
            "needs overlay permission for schedule fire",
            PermissionChecker.isOverlayPermissionGranted(context)
        )

        val fireResult = buildScheduleCheckWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), fireResult)

        val active = sessionRepository.getActiveSessionSync()
        assertNotNull("scheduled fire must create a live lock", active)
        assertEquals(SessionStatus.ACTIVE, active!!.status)
        assertTrue("runtime enforcement must engage when the schedule fires", enforcementStore.activeSession)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = nm.activeNotifications.firstOrNull { it.id == SessionNotificationManager.SCHEDULE_NOTICE_ID }
        assertNotNull("scheduled-start notice must be posted", posted)
        val hourText = (startMinutes / 60).toString().padStart(2, '0')
        assertTrue(
            "notice must name the window start hour",
            posted!!.notification.extras.getString("android.text")!!.contains(hourText)
        )
        // The ongoing session notification (id 1001) is deliberately not
        // asserted here: MIUI drops ongoing notifications while the app has no
        // completed foreground service, and the services this test starts are
        // torn down milliseconds after the fire. Its posting is covered by
        // SessionNotificationManager unit tests; this journey asserts the
        // feature-owned notice (1005) above.

        val row = database.sessionDao().getActiveSessionSync()!!
        database.sessionDao().updateSession(row.copy(expiresAt = System.currentTimeMillis() - 1_000L))

        val expiryResult = buildExpiryWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), expiryResult)
        assertNull("expired scheduled session must complete like a manual one", sessionRepository.getActiveSessionSync())
        assertFalse("runtime enforcement must disengage on completion", enforcementStore.activeSession)
    }
}
