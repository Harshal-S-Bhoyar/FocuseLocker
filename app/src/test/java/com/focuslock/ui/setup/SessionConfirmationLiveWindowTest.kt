package com.focuslock.ui.setup

import androidx.lifecycle.SavedStateHandle
import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.TrustedContactRepository
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.ActivationResult
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import com.focuslock.domain.usecase.ScheduleEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SessionConfirmationLiveWindowTest {

    private val scheduleRepository = mockk<ScheduleRepository>()
    private val scheduleEngine = mockk<ScheduleEngine>()
    private val activateSessionUseCase = mockk<ActivateSessionUseCase>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SessionConfirmationViewModel(
        SavedStateHandle(mapOf("durationMillis" to 3600000L, "live" to true)),
        mockk<GetAllCategoriesUseCase>(relaxed = true).also {
            every { it.invoke() } returns flowOf(emptyList())
        },
        mockk<GetAllKeywordsUseCase>(relaxed = true).also {
            every { it.invoke() } returns flowOf(emptyList())
        },
        mockk<TrustedContactRepository>(relaxed = true).also {
            every { it.getVerifiedContact() } returns flowOf(null)
        },
        activateSessionUseCase,
        scheduleRepository,
        scheduleEngine
    )

    @Test
    fun `ended window reports an error and skips activation`() = runTest {
        coEvery { scheduleRepository.getWindowsSync() } returns emptyList()
        val vm = viewModel()

        vm.lockInSession(LocalDateTime.of(2026, 10, 13, 12, 30))

        val state = vm.activationState.value
        assertTrue(state is SessionConfirmationViewModel.ActivationState.Error)
        assertEquals(
            "The scheduled window has ended.",
            (state as SessionConfirmationViewModel.ActivationState.Error).message
        )
        coVerify(exactly = 0) { activateSessionUseCase(any()) }
    }

    @Test
    fun `due window activates with current remaining instead of the original duration`() = runTest {
        val window = ScheduleWindowEntity(7L, 2, 720, 780)
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(window)
        every { scheduleEngine.dueWindow(any(), any(), any()) } returns window
        every { scheduleEngine.remainingMillis(any(), any()) } returns 1_800_000L
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.Activated(9L, 0L)
        val vm = viewModel()

        vm.lockInSession(LocalDateTime.of(2026, 10, 13, 12, 30))

        assertTrue(vm.activationState.value is SessionConfirmationViewModel.ActivationState.Success)
        coVerify(exactly = 1) { activateSessionUseCase(1_800_000L) }
    }
}
