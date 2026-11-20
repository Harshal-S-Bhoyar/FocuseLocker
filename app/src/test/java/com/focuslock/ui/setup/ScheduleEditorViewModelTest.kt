package com.focuslock.ui.setup

import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.ActivationResult
import com.focuslock.domain.usecase.ReconcileScheduledSessionUseCase
import com.focuslock.domain.usecase.ScheduleEngine
import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.worker.WorkScheduler
import com.focuslock.domain.usecase.HasVerifiedContactUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleEditorViewModelTest {

    private lateinit var repository: ScheduleRepository
    private lateinit var hasVerifiedContactUseCase: HasVerifiedContactUseCase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var activateSessionUseCase: ActivateSessionUseCase
    private lateinit var workScheduler: WorkScheduler
    private lateinit var sessionRepository: SessionRepository
    private lateinit var reconcileScheduledSessionUseCase: ReconcileScheduledSessionUseCase
    private lateinit var viewModel: ScheduleEditorViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val stored = MutableStateFlow<List<ScheduleWindowEntity>>(emptyList())

    private val now = LocalDateTime.of(2026, 10, 13, 12, 30)

    private fun epoch(at: LocalDateTime): Long = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun runningSession(expiresAt: Long) = Session(
        id = 5L,
        status = SessionStatus.ACTIVE,
        startedAt = 0L,
        expiresAt = expiresAt,
        unlockedEarly = false,
        unlockRequestedAt = null,
        createdAt = 0L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        hasVerifiedContactUseCase = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        activateSessionUseCase = mockk(relaxed = true)
        workScheduler = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        reconcileScheduledSessionUseCase = mockk(relaxed = true)

        every { repository.getWindows() } returns stored
        every { repository.isScheduleEnabled() } returns MutableStateFlow(false)
        coEvery { repository.getWindowsSync() } answers { stored.value }
        coEvery { hasVerifiedContactUseCase() } returns true
        coEvery { repository.upsertWindow(any()) } answers {
            val w = firstArg<ScheduleWindowEntity>().copy(id = 1L)
            stored.value = stored.value + w
            1L
        }
        viewModel = ScheduleEditorViewModel(
            repository,
            ScheduleEngine(),
            hasVerifiedContactUseCase,
            appSettingsRepository,
            activateSessionUseCase,
            workScheduler,
            sessionRepository,
            reconcileScheduledSessionUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid window saves and clears error`() = runTest(testDispatcher) {
        val saved = viewModel.saveWindow(null, 1, 1200, 840)
        advanceUntilIdle()

        assertTrue(saved)
        assertNull(viewModel.error.value)
        coVerify { repository.upsertWindow(match { it.weekday == 1 && it.startMinutes == 1200 }) }
    }

    @Test
    fun `overlapping window refused with warning and no upsert`() = runTest(testDispatcher) {
        stored.value = listOf(ScheduleWindowEntity(id = 1L, weekday = 1, startMinutes = 1200, endMinutes = 840))
        val collectJob = launch { viewModel.windows.collect {} }
        advanceUntilIdle()

        val saved = viewModel.saveWindow(null, 2, 600, 720)
        advanceUntilIdle()

        assertFalse(saved)
        assertTrue(viewModel.error.value!!.startsWith("Overlapping with"))
        coVerify(exactly = 0) { repository.upsertWindow(any()) }
        collectJob.cancel()
    }

    @Test
    fun `invalid window refused with reason`() = runTest(testDispatcher) {
        val saved = viewModel.saveWindow(null, 9, 0, 60)
        advanceUntilIdle()

        assertFalse(saved)
        assertEquals("weekday 9 outside 1 to 7", viewModel.error.value)
    }

    @Test
    fun `editing own window does not collide with itself`() = runTest(testDispatcher) {
        stored.value = listOf(ScheduleWindowEntity(id = 1L, weekday = 1, startMinutes = 1200, endMinutes = 840))
        val collectJob = launch { viewModel.windows.collect {} }
        advanceUntilIdle()

        val saved = viewModel.saveWindow(1L, 1, 1200, 840)
        advanceUntilIdle()

        assertTrue(saved)
        assertNull(viewModel.error.value)
        collectJob.cancel()
    }

    @Test
    fun `delete clears error and removes row`() = runTest(testDispatcher) {
        stored.value = listOf(ScheduleWindowEntity(id = 1L, weekday = 1, startMinutes = 1200, endMinutes = 840))

        viewModel.deleteWindow(1L)
        advanceUntilIdle()

        coVerify { repository.deleteWindow(1L) }
        assertNull(viewModel.error.value)
    }

    @Test
    fun `switch toggle reaches repository`() = runTest(testDispatcher) {
        viewModel.setScheduleEnabled(true)
        advanceUntilIdle()

        coVerify { repository.setScheduleEnabled(true) }
    }

    @Test
    fun `full-day preset formats to midnight bounds`() {
        assertEquals("00:00", viewModel.formatMinutes(0))
        assertEquals("23:59", viewModel.formatMinutes(1439))
        assertTrue(viewModel.formatWindow(ScheduleWindowEntity(weekday = 6, startMinutes = 0, endMinutes = 1439)).contains("23:59"))
    }

    @Test
    fun `hasVerifiedContact emits value from use case`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertTrue(viewModel.hasVerifiedContact.value)
    }

    @Test
    fun `armScheduleAndCheckActivation sets enabled and completes onboarding`() = runTest(testDispatcher) {
        val outcome = viewModel.armScheduleAndCheckActivation()
        advanceUntilIdle()

        assertEquals(ScheduleArmOutcome.ArmedIdle, outcome)
        coVerify { repository.setScheduleEnabled(true) }
        coVerify { workScheduler.scheduleScheduleChecks() }
        coVerify { appSettingsRepository.setOnboardingComplete() }
    }

    @Test
    fun `armScheduleAndCheckActivation starts active session with the exact window remainder`() = runTest(testDispatcher) {
        val weekday = now.dayOfWeek.value
        val minuteNow = now.hour * 60 + now.minute
        val startMin = (minuteNow - 10).coerceAtLeast(0)
        val endMin = (minuteNow + 30).coerceAtMost(1439)
        stored.value = listOf(ScheduleWindowEntity(id = 1L, weekday = weekday, startMinutes = startMin, endMinutes = endMin))
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.Activated(42L, epoch(now) + 1_800_000L)

        val outcome = viewModel.armScheduleAndCheckActivation(now)
        advanceUntilIdle()

        assertTrue("a due window must hand back its remaining duration: $outcome", outcome is ScheduleArmOutcome.LiveWindow)
        assertEquals(1_800_000L, (outcome as ScheduleArmOutcome.LiveWindow).remainingMillis)
        coVerify { activateSessionUseCase(1_800_000L) }
        // The serviced occurrence is recorded exactly like the worker records it.
        coVerify { repository.setLastFiredSchedule("1:2026-10-13") }
    }

    @Test
    fun `arm hands off to the running session instead of failing`() = runTest(testDispatcher) {
        stored.value = listOf(ScheduleWindowEntity(id = 1L, weekday = now.dayOfWeek.value, startMinutes = 720, endMinutes = 780))
        val sessionExpires = epoch(now) + 20 * 60 * 1000L
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.AlreadyActive(runningSession(sessionExpires))
        coEvery { reconcileScheduledSessionUseCase(any()) } returns
            ReconcileScheduledSessionUseCase.Result.Unchanged(sessionExpires)

        val outcome = viewModel.armScheduleAndCheckActivation(now)
        advanceUntilIdle()

        assertTrue("a live session is a hand-off, never a failure: $outcome", outcome is ScheduleArmOutcome.LiveWindow)
        assertEquals(20 * 60 * 1000L, (outcome as ScheduleArmOutcome.LiveWindow).remainingMillis)
        assertNull("no error banner for a healthy running session", viewModel.error.value)
        coVerify(exactly = 0) { repository.setLastFiredSchedule(any()) }
    }

    @Test
    fun `arm extends the running session when the due window ends later`() = runTest(testDispatcher) {
        stored.value = listOf(ScheduleWindowEntity(id = 1L, weekday = now.dayOfWeek.value, startMinutes = 720, endMinutes = 1080))
        val sessionExpires = epoch(now) + 20 * 60 * 1000L
        val extended = epoch(now) + 90 * 60 * 1000L
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.AlreadyActive(runningSession(sessionExpires))
        coEvery { reconcileScheduledSessionUseCase(any()) } returns
            ReconcileScheduledSessionUseCase.Result.Extended(extended)

        val outcome = viewModel.armScheduleAndCheckActivation(now)
        advanceUntilIdle()

        assertTrue(outcome is ScheduleArmOutcome.LiveWindow)
        assertEquals(90 * 60 * 1000L, (outcome as ScheduleArmOutcome.LiveWindow).remainingMillis)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `arm ignores a concurrent second tap while arming is in flight`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.setScheduleEnabled(any()) } coAnswers { gate.await() }
        viewModel = ScheduleEditorViewModel(
            repository,
            ScheduleEngine(),
            hasVerifiedContactUseCase,
            appSettingsRepository,
            activateSessionUseCase,
            workScheduler,
            sessionRepository,
            reconcileScheduledSessionUseCase
        )

        val first = async { viewModel.armScheduleAndCheckActivation(now) }
        runCurrent()

        val second = viewModel.armScheduleAndCheckActivation(now)
        assertEquals(ScheduleArmOutcome.ArmInFlight, second)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ScheduleArmOutcome.ArmedIdle, first.await())
    }

    @Test
    fun `saveWindow extending the due window extends the running session with a notice`() = runTest(testDispatcher) {
        stored.value = listOf(ScheduleWindowEntity(id = 1L, weekday = now.dayOfWeek.value, startMinutes = 720, endMinutes = 780))
        val collectJob = launch { viewModel.windows.collect {} }
        advanceUntilIdle()
        val sessionExpires = epoch(now) + 10 * 60 * 1000L
        val extended = epoch(now) + 90 * 60 * 1000L
        coEvery { sessionRepository.getActiveSessionSync() } returns runningSession(sessionExpires)
        coEvery { reconcileScheduledSessionUseCase(any()) } returns
            ReconcileScheduledSessionUseCase.Result.Extended(extended)

        val saved = viewModel.saveWindow(1L, now.dayOfWeek.value, 720, 840, now)
        advanceUntilIdle()

        assertTrue(saved)
        coVerify { reconcileScheduledSessionUseCase(now) }
        assertTrue(
            "an extended window must say the session now runs longer: ${viewModel.notice.value}",
            viewModel.notice.value!!.contains("now runs until 14:00")
        )
        collectJob.cancel()
    }

    @Test
    fun `saveWindow shortening the due window keeps the running session with a notice`() = runTest(testDispatcher) {
        stored.value = listOf(ScheduleWindowEntity(id = 1L, weekday = now.dayOfWeek.value, startMinutes = 720, endMinutes = 840))
        val collectJob = launch { viewModel.windows.collect {} }
        advanceUntilIdle()
        val sessionExpires = epoch(now) + 60 * 60 * 1000L
        coEvery { sessionRepository.getActiveSessionSync() } returns runningSession(sessionExpires)
        coEvery { reconcileScheduledSessionUseCase(any()) } returns
            ReconcileScheduledSessionUseCase.Result.Unchanged(sessionExpires)

        val saved = viewModel.saveWindow(1L, now.dayOfWeek.value, 720, 780, now)
        advanceUntilIdle()

        assertTrue(saved)
        val notice = viewModel.notice.value!!
        assertTrue("shortening must not cancel the session: $notice", notice.contains("will finish at 13:30"))
        assertFalse("shortening must not look like an extension", notice.contains("runs until"))
        collectJob.cancel()
    }

    @Test
    fun `deleteWindow removing the due window keeps the running session with a notice`() = runTest(testDispatcher) {
        stored.value = listOf(ScheduleWindowEntity(id = 1L, weekday = now.dayOfWeek.value, startMinutes = 720, endMinutes = 840))
        val collectJob = launch { viewModel.windows.collect {} }
        advanceUntilIdle()
        val sessionExpires = epoch(now) + 60 * 60 * 1000L
        coEvery { sessionRepository.getActiveSessionSync() } returns runningSession(sessionExpires)
        coEvery { reconcileScheduledSessionUseCase(any()) } returns
            ReconcileScheduledSessionUseCase.Result.Unchanged(sessionExpires)

        viewModel.deleteWindow(1L, now)
        advanceUntilIdle()

        coVerify { repository.deleteWindow(1L) }
        assertTrue(viewModel.notice.value!!.contains("will finish at 13:30"))
        collectJob.cancel()
    }

    @Test
    fun `pausing the schedule keeps the running session with a notice`() = runTest(testDispatcher) {
        val sessionExpires = epoch(now) + 45 * 60 * 1000L
        coEvery { sessionRepository.getActiveSessionSync() } returns runningSession(sessionExpires)

        viewModel.setScheduleEnabled(false, now)
        advanceUntilIdle()

        coVerify { repository.setScheduleEnabled(false) }
        val notice = viewModel.notice.value!!
        assertTrue("pausing must explain the live session survives: $notice", notice.contains("paused for upcoming sessions"))
        assertTrue(notice.contains("finish at 13:15"))
        coVerify(exactly = 0) { reconcileScheduledSessionUseCase(any()) }
    }
}
