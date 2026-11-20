package com.focuslock.ui.setup

import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.worker.WorkScheduler
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.ActivationResult
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleArmingFailureTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ScheduleRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var activateSessionUseCase: ActivateSessionUseCase
    private lateinit var viewModel: ScheduleEditorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        activateSessionUseCase = mockk(relaxed = true)
        every { repository.getWindows() } returns MutableStateFlow(emptyList())
        every { repository.isScheduleEnabled() } returns MutableStateFlow(false)
        coEvery { repository.getWindowsSync() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelWith(workScheduler: WorkScheduler = mockk(relaxed = true)) =
        ScheduleEditorViewModel(
            repository,
            ScheduleEngine(),
            mockk(relaxed = true),
            appSettingsRepository,
            activateSessionUseCase,
            workScheduler,
            mockk<SessionRepository>(relaxed = true),
            mockk<ReconcileScheduledSessionUseCase>(relaxed = true)
        )

    @Test
    fun `enable failure stops arming before onboarding completes`() = runTest(testDispatcher) {
        coEvery { repository.setScheduleEnabled(any()) } throws RuntimeException("db locked")
        viewModel = viewModelWith()

        val outcome = viewModel.armScheduleAndCheckActivation()
        advanceUntilIdle()

        assertEquals(ScheduleArmOutcome.Failed, outcome)
        // Windows are read before anything is armed, so the read still happens.
        coVerify(exactly = 1) { repository.getWindowsSync() }
        coVerify(exactly = 0) { appSettingsRepository.setOnboardingComplete() }
    }

    @Test
    fun `successful enable still completes onboarding`() = runTest(testDispatcher) {
        viewModel = viewModelWith()

        viewModel.armScheduleAndCheckActivation()
        advanceUntilIdle()

        coVerify { appSettingsRepository.setOnboardingComplete() }
    }

    @Test
    fun `checks enqueue failure restores the prior flag and stops arming`() = runTest(testDispatcher) {
        // Regression: this failure was only logged, so arming reported success for a schedule whose
        // periodic worker was never enqueued — a schedule that could never fire.
        val workScheduler = mockk<WorkScheduler>(relaxed = true)
        every { workScheduler.scheduleScheduleChecks() } throws RuntimeException("workmanager unavailable")
        viewModel = viewModelWith(workScheduler)

        val outcome = viewModel.armScheduleAndCheckActivation()
        advanceUntilIdle()

        assertEquals("arming must fail when its checks cannot be enqueued", ScheduleArmOutcome.Failed, outcome)
        coVerify(exactly = 0) { appSettingsRepository.setOnboardingComplete() }
        coVerify { repository.setScheduleEnabled(false) }
        assertNotNull("the failure must be surfaced, not only logged", viewModel.error.value)
    }

    @Test
    fun `onboarding failure keeps setup retryable instead of parking on ArmedIdle`() = runTest(testDispatcher) {
        // The schedule and its checks are in place, but setup is not complete: the terminal
        // armed state would strand the user with Done disabled and no way to finish.
        coEvery { appSettingsRepository.setOnboardingComplete() } throws RuntimeException("db locked")
        viewModel = viewModelWith()

        val outcome = viewModel.armScheduleAndCheckActivation()
        advanceUntilIdle()

        assertEquals(ScheduleArmOutcome.Failed, outcome)
        coVerify { repository.setScheduleEnabled(true) }
        assertNotNull("the failure must be surfaced, not only logged", viewModel.error.value)
    }

    @Test
    fun `storage failure fails the arming instead of escaping as a crash`() = runTest(testDispatcher) {
        // Regression: the saved-window read was the one step with no guard, so a storage failure
        // escaped the suspend function into the screen's coroutine — a crash, not an error.
        coEvery { repository.getWindowsSync() } throws IllegalStateException("cursor closed")
        viewModel = viewModelWith()

        val outcome = runCatching { viewModel.armScheduleAndCheckActivation() }
        advanceUntilIdle()

        assertEquals("arming must report failure, not throw", ScheduleArmOutcome.Failed, outcome.getOrNull())
        assertNotNull("the failure must be surfaced to the screen", viewModel.error.value)
    }

    @Test
    fun `immediate activation failure reports distinctly with retry available`() = runTest(testDispatcher) {
        val now = LocalDateTime.of(2026, 10, 13, 12, 30)
        coEvery { repository.getWindowsSync() } returns listOf(
            ScheduleWindowEntity(1L, now.dayOfWeek.value, 12 * 60, 13 * 60)
        )
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.Failed(Exception("conflict"))
        viewModel = viewModelWith()

        val outcome = viewModel.armScheduleAndCheckActivation(now)
        advanceUntilIdle()

        assertTrue(
            "failed immediate start must not park on the terminal armed state: $outcome",
            outcome is ScheduleArmOutcome.ActivationFailed
        )
        assertNotNull("the failure must be surfaced, not only logged", viewModel.error.value)
        coVerify { appSettingsRepository.setOnboardingComplete() }
    }
}
