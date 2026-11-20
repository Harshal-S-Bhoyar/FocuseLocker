package com.focuslock.ui.setup

import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.worker.WorkScheduler
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.ActivationResult
import com.focuslock.domain.usecase.HasVerifiedContactUseCase
import com.focuslock.domain.usecase.ReconcileScheduledSessionUseCase
import com.focuslock.domain.usecase.ScheduleEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Adversarial test suite for the Schedule setup flow and immediate activation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleFlowAdversarialTest {

    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var scheduleEngine: ScheduleEngine
    private lateinit var hasVerifiedContactUseCase: HasVerifiedContactUseCase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var activateSessionUseCase: ActivateSessionUseCase
    private lateinit var workScheduler: WorkScheduler
    private lateinit var viewModel: ScheduleEditorViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val windowsFlow = MutableStateFlow<List<ScheduleWindowEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scheduleRepository = mockk(relaxed = true)
        scheduleEngine = ScheduleEngine()
        hasVerifiedContactUseCase = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        activateSessionUseCase = mockk(relaxed = true)
        workScheduler = mockk(relaxed = true)

        every { scheduleRepository.getWindows() } returns windowsFlow
        every { scheduleRepository.isScheduleEnabled() } returns MutableStateFlow(false)
        coEvery { scheduleRepository.getWindowsSync() } answers { windowsFlow.value }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // A1 [state]: User has no verified contact -> hasVerifiedContact is false
    @Test
    fun advA1_unverifiedContact_flagsFalseForRouting() = runTest(testDispatcher) {
        coEvery { hasVerifiedContactUseCase() } returns false
        viewModel = ScheduleEditorViewModel(
            scheduleRepository,
            scheduleEngine,
            hasVerifiedContactUseCase,
            appSettingsRepository,
            activateSessionUseCase,
            workScheduler,
            mockk<SessionRepository>(relaxed = true),
            mockk<ReconcileScheduledSessionUseCase>(relaxed = true)
        )
        advanceUntilIdle()

        assertFalse("Unverified contact must expose false so flow redirects to TrustedContactSetup", viewModel.hasVerifiedContact.value)
    }

    // A2 [state]: User has verified contact -> hasVerifiedContact is true
    @Test
    fun advA2_verifiedContact_flagsTrueForDirectConfirmation() = runTest(testDispatcher) {
        coEvery { hasVerifiedContactUseCase() } returns true
        viewModel = ScheduleEditorViewModel(
            scheduleRepository,
            scheduleEngine,
            hasVerifiedContactUseCase,
            appSettingsRepository,
            activateSessionUseCase,
            workScheduler,
            mockk<SessionRepository>(relaxed = true),
            mockk<ReconcileScheduledSessionUseCase>(relaxed = true)
        )
        advanceUntilIdle()

        assertTrue("Verified contact must expose true so flow proceeds to ScheduleConfirm", viewModel.hasVerifiedContact.value)
    }

    // A3 [boundary]: Immediate activation succeeds when now is within window
    @Test
    fun advA3_windowDueNow_triggersImmediateActivation() = runTest(testDispatcher) {
        coEvery { hasVerifiedContactUseCase() } returns true
        // Fixed time: window and activation check share it, so no wall-clock flake.
        val now = LocalDateTime.of(2026, 10, 13, 12, 30)
        val weekday = now.dayOfWeek.value
        val minuteNow = now.hour * 60 + now.minute
        val startMin = (minuteNow - 15).coerceAtLeast(0)
        val endMin = (minuteNow + 45).coerceAtMost(1439)

        windowsFlow.value = listOf(
            ScheduleWindowEntity(id = 10L, weekday = weekday, startMinutes = startMin, endMinutes = endMin)
        )
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.Activated(999L, 0L)

        viewModel = ScheduleEditorViewModel(
            scheduleRepository,
            scheduleEngine,
            hasVerifiedContactUseCase,
            appSettingsRepository,
            activateSessionUseCase,
            workScheduler,
            mockk<SessionRepository>(relaxed = true),
            mockk<ReconcileScheduledSessionUseCase>(relaxed = true)
        )
        val outcome = viewModel.armScheduleAndCheckActivation(now)
        advanceUntilIdle()

        assertTrue(
            "Due window must trigger active session immediately: $outcome",
            outcome is ScheduleArmOutcome.LiveWindow
        )
        coVerify { activateSessionUseCase(any()) }
        coVerify { scheduleRepository.setScheduleEnabled(true) }
        coVerify { appSettingsRepository.setOnboardingComplete() }
    }

    // A4 [boundary]: Outside window -> arms schedule, completes onboarding, returns false
    @Test
    fun advA4_windowNotDue_armsWithoutActivation() = runTest(testDispatcher) {
        coEvery { hasVerifiedContactUseCase() } returns true
        val now = LocalDateTime.now()
        val oppositeDay = if (now.dayOfWeek.value == 7) 1 else now.dayOfWeek.value + 1

        windowsFlow.value = listOf(
            ScheduleWindowEntity(id = 11L, weekday = oppositeDay, startMinutes = 100, endMinutes = 200)
        )

        viewModel = ScheduleEditorViewModel(
            scheduleRepository,
            scheduleEngine,
            hasVerifiedContactUseCase,
            appSettingsRepository,
            activateSessionUseCase,
            workScheduler,
            mockk<SessionRepository>(relaxed = true),
            mockk<ReconcileScheduledSessionUseCase>(relaxed = true)
        )
        val outcome = viewModel.armScheduleAndCheckActivation()
        advanceUntilIdle()

        assertEquals(
            "Non-due window must reach the terminal armed state, not a failure",
            ScheduleArmOutcome.ArmedIdle,
            outcome
        )
        coVerify(exactly = 0) { activateSessionUseCase(any()) }
        coVerify { scheduleRepository.setScheduleEnabled(true) }
        coVerify { workScheduler.scheduleScheduleChecks() }
        coVerify { appSettingsRepository.setOnboardingComplete() }
    }

    // A5 [property/fuzz]: Fuzz random window sets; armScheduleAndCheckActivation must never throw or corrupt state
    @Test
    fun advA5_propertyFuzz_randomWindowsNeverCrashArming() = runTest(testDispatcher) {
        coEvery { hasVerifiedContactUseCase() } returns true
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.Activated(1L, 0L)
        val rng = Random(42)

        for (trial in 1..20) {
            val randomWeekday = rng.nextInt(1, 8)
            val s = rng.nextInt(0, 1400)
            val e = rng.nextInt(s + 1, 1440)
            windowsFlow.value = listOf(
                ScheduleWindowEntity(id = trial.toLong(), weekday = randomWeekday, startMinutes = s, endMinutes = e)
            )

            viewModel = ScheduleEditorViewModel(
                scheduleRepository,
                scheduleEngine,
                hasVerifiedContactUseCase,
                appSettingsRepository,
                activateSessionUseCase,
                workScheduler,
                mockk<SessionRepository>(relaxed = true),
                mockk<ReconcileScheduledSessionUseCase>(relaxed = true)
            )
            val result = runCatching { viewModel.armScheduleAndCheckActivation() }
            assertTrue("Arming must never throw on valid window trial $trial", result.isSuccess)
            // Whatever the random window set produced, it is either the terminal armed state or a
            // live hand-off — never the failure state, which would mean a valid schedule was
            // reported as unarmed.
            assertTrue(
                "trial $trial reported a failed arming: ${result.getOrNull()}",
                result.getOrThrow() != ScheduleArmOutcome.Failed
            )
        }
    }
}
