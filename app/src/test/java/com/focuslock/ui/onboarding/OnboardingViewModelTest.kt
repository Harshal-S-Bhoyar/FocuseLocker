package com.focuslock.ui.onboarding

import com.focuslock.domain.usecase.CheckOnboardingStatusUseCase
import com.focuslock.domain.usecase.InitializeAppUseCase
import com.focuslock.domain.usecase.OnboardingStatus
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.domain.permission.AppPermissionStatus
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
    private val permissionStatusProvider: AppPermissionStatusProvider = mockk()

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
        coEvery { permissionStatusProvider.getStatus() } returns AppPermissionStatus(false, false, false, false, false, false)

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase, permissionStatusProvider)

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
        coEvery { permissionStatusProvider.getStatus() } returns AppPermissionStatus(true, true, true, true, true, true)

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase, permissionStatusProvider)
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
        coEvery { permissionStatusProvider.getStatus() } returns AppPermissionStatus(true, true, true, true, true, true)

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase, permissionStatusProvider)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(OnboardingEvent.NavigateToActiveSession, viewModel.uiState.value.event)
    }

    @Test
    fun `when initialization fails loading is dismissed and no event is sent`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } throws RuntimeException("Database error")
        coEvery { permissionStatusProvider.getStatus() } returns AppPermissionStatus(false, false, false, false, false, false)

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase, permissionStatusProvider)
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
        coEvery { permissionStatusProvider.getStatus() } returns AppPermissionStatus(true, true, true, true, true, true)

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase, permissionStatusProvider)
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
        coEvery { permissionStatusProvider.getStatus() } returns AppPermissionStatus(true, true, true, true, true, true)

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase, permissionStatusProvider)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onGetStartedClicked()
        assertEquals(OnboardingEvent.NavigateToPermissions, viewModel.uiState.value.event)

        viewModel.onEventConsumed()
        assertNull(viewModel.uiState.value.event)
    }

    @Test
    fun `when active session exists but accessibility is missing and overlay is granted triggers StartPermissionEnforcement`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = true,
            hasVerifiedContact = true,
            hasActiveSession = true
        )
        coEvery { permissionStatusProvider.getStatus() } returns AppPermissionStatus(
            accessibilityGranted = false,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true,
            notificationGranted = true
        )

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase, permissionStatusProvider)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(OnboardingEvent.StartPermissionEnforcement, viewModel.uiState.value.event)
    }

    @Test
    fun `when active session exists but accessibility and overlay are missing navigates to PermissionRepair`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = true,
            hasVerifiedContact = true,
            hasActiveSession = true
        )
        coEvery { permissionStatusProvider.getStatus() } returns AppPermissionStatus(
            accessibilityGranted = false,
            deviceAdminGranted = true,
            overlayGranted = false,
            vpnGranted = true,
            batteryOptimizationIgnored = true,
            notificationGranted = true
        )

        viewModel = OnboardingViewModel(initializeAppUseCase, checkOnboardingStatusUseCase, permissionStatusProvider)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(OnboardingEvent.NavigateToPermissionRepair, viewModel.uiState.value.event)
    }
}
