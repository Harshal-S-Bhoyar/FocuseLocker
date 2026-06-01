package com.focuslock.ui.onboarding

import com.focuslock.domain.usecase.CheckOnboardingStatusUseCase
import com.focuslock.domain.usecase.InitializeAppUseCase
import com.focuslock.domain.usecase.OnboardingStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: OnboardingViewModel
    private val initializeAppUseCase: InitializeAppUseCase = mockk(relaxed = true)
    private val checkOnboardingStatusUseCase: CheckOnboardingStatusUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = false,
            hasVerifiedContact = false,
            hasActiveSession = false
        )

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase)

        // Verify initial state is loading
        assertEquals(true, viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.event)
    }

    @Test
    fun `when initialization succeeds and no active session welcome screen is shown`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = false,
            hasVerifiedContact = false,
            hasActiveSession = false
        )

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.event)
    }

    @Test
    fun `when initialization succeeds and active session exists navigates to active session`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = true,
            hasVerifiedContact = true,
            hasActiveSession = true
        )

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(OnboardingEvent.NavigateToActiveSession, viewModel.uiState.value.event)
    }

    @Test
    fun `when initialization fails loading is dismissed and no event is sent`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } throws RuntimeException("Database error")

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.event)
    }

    @Test
    fun `onGetStartedClicked triggers navigation to permissions`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = false,
            hasVerifiedContact = false,
            hasActiveSession = false
        )

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onGetStartedClicked()

        assertEquals(OnboardingEvent.NavigateToPermissions, viewModel.uiState.value.event)
    }

    @Test
    fun `onEventConsumed clears the navigation event`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = false,
            hasVerifiedContact = false,
            hasActiveSession = false
        )

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onGetStartedClicked()
        assertEquals(OnboardingEvent.NavigateToPermissions, viewModel.uiState.value.event)

        viewModel.onEventConsumed()
        assertNull(viewModel.uiState.value.event)
    }
}
