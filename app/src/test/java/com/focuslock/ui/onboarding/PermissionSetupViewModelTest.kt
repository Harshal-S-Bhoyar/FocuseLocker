package com.focuslock.ui.onboarding

import com.focuslock.domain.permission.AppPermissionStatus
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.data.repository.AppSettingsRepository
import io.mockk.coVerify
import io.mockk.every
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PermissionSetupViewModel
    private val permissionStatusProvider: AppPermissionStatusProvider = mockk()
    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { permissionStatusProvider.getStatus() } returns permissionStatus()
        io.mockk.coEvery { appSettingsRepository.setSetting(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has all permissions set to false`() {
        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository)

        val state = viewModel.uiState.value
        assertFalse(state.permissions.accessibilityGranted)
        assertFalse(state.permissions.deviceAdminGranted)
        assertFalse(state.permissions.overlayGranted)
        assertFalse(state.permissions.batteryOptimizationIgnored)
        assertFalse(state.permissions.vpnGranted)
        assertFalse(state.permissions.notificationGranted)
        assertFalse(state.permissions.allCriticalGranted)
        assertFalse(state.navigateToTrustedContact)
    }

    @Test
    fun `refreshPermissions correctly maps active permissions`() {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            batteryOptimizationIgnored = true,
            notificationGranted = true
        )

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository)
        viewModel.refreshPermissions()

        val permissions = viewModel.uiState.value.permissions
        assertTrue(permissions.accessibilityGranted)
        assertTrue(permissions.deviceAdminGranted)
        assertFalse(permissions.overlayGranted)
        assertTrue(permissions.batteryOptimizationIgnored)
        assertFalse(permissions.vpnGranted)
        assertTrue(permissions.notificationGranted)
        assertFalse(permissions.allCriticalGranted) // overlay is critical and false
    }

    @Test
    fun `when all critical permissions are granted allCriticalGranted is true`() {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true
        )

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository)
        viewModel.refreshPermissions()

        val permissions = viewModel.uiState.value.permissions
        assertTrue(permissions.accessibilityGranted)
        assertTrue(permissions.deviceAdminGranted)
        assertTrue(permissions.overlayGranted)
        assertTrue(permissions.allCriticalGranted)
    }

    @Test
    fun `onContinueClicked does nothing when critical permissions are missing`() = runTest {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true
        )

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository)
        viewModel.refreshPermissions()

        viewModel.onContinueClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.navigateToTrustedContact)
        coVerify(exactly = 0) { appSettingsRepository.setSetting(any(), any()) }
    }

    @Test
    fun `onContinueClicked saves setting and navigates when critical permissions are granted`() = runTest {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true
        )

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository)
        viewModel.refreshPermissions()

        viewModel.onContinueClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.navigateToTrustedContact)
        coVerify(exactly = 1) { appSettingsRepository.setSetting("permissions_granted", "true") }
    }

    @Test
    fun `onNavigationConsumed resets navigation flag`() = runTest {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true
        )

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository)
        viewModel.refreshPermissions()

        viewModel.onContinueClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.navigateToTrustedContact)

        viewModel.onNavigationConsumed()
        assertFalse(viewModel.uiState.value.navigateToTrustedContact)
    }

    @Test
    fun `onContinueClicked does nothing when batteryOptimizationIgnored is missing`() = runTest {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = false
        )

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository)
        viewModel.refreshPermissions()

        val permissions = viewModel.uiState.value.permissions
        assertFalse(permissions.allCriticalGranted)

        viewModel.onContinueClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.navigateToTrustedContact)
        coVerify(exactly = 0) { appSettingsRepository.setSetting(any(), any()) }
    }

    private fun permissionStatus(
        accessibilityGranted: Boolean = false,
        deviceAdminGranted: Boolean = false,
        overlayGranted: Boolean = false,
        batteryOptimizationIgnored: Boolean = false,
        vpnGranted: Boolean = false,
        notificationGranted: Boolean = false
    ) = AppPermissionStatus(
        accessibilityGranted = accessibilityGranted,
        deviceAdminGranted = deviceAdminGranted,
        overlayGranted = overlayGranted,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        vpnGranted = vpnGranted,
        notificationGranted = notificationGranted
    )
}
