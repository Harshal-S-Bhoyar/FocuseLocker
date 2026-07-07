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
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import android.content.Context
import android.content.Intent
import com.focuslock.util.OemAutostartHelper
import io.mockk.mockkObject
import io.mockk.unmockkObject

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PermissionSetupViewModel
    private val permissionStatusProvider: AppPermissionStatusProvider = mockk()
    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(OemAutostartHelper)
        every { OemAutostartHelper.getAutostartIntent(any()) } returns null
        every { permissionStatusProvider.getStatus() } returns permissionStatus()
        io.mockk.coEvery { appSettingsRepository.setSetting(any(), any()) } returns Unit
        every { appSettingsRepository.getSetting(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(OemAutostartHelper)
    }

    @Test
    fun `initial state has all permissions set to false`() {
        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)

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
    fun `refreshPermissions correctly maps active permissions`() = runTest {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            batteryOptimizationIgnored = true,
            notificationGranted = true
        )

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

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
    fun `when all critical permissions are granted allCriticalGranted is true`() = runTest {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true
        )

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

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

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

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

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

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

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

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

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

        val permissions = viewModel.uiState.value.permissions
        assertFalse(permissions.allCriticalGranted)

        viewModel.onContinueClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.navigateToTrustedContact)
        coVerify(exactly = 0) { appSettingsRepository.setSetting(any(), any()) }
    }

    @Test
    fun `refreshPermissions maps autostart intent availability`() = runTest {
        every { OemAutostartHelper.getAutostartIntent(any()) } returns mockk(relaxed = true)
        every { appSettingsRepository.getSetting("autostart_acknowledged") } returns flowOf("true")

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

        val permissions = viewModel.uiState.value.permissions
        assertTrue(permissions.hasAutostartIntent)
        assertTrue(permissions.autostartAcknowledged)
    }

    @Test
    fun `when autostart is needed but not acknowledged allCriticalGranted is false`() = runTest {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true
        )
        every { OemAutostartHelper.getAutostartIntent(any()) } returns mockk(relaxed = true)
        every { appSettingsRepository.getSetting("autostart_acknowledged") } returns flowOf("false")

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

        val permissions = viewModel.uiState.value.permissions
        assertTrue(permissions.hasAutostartIntent)
        assertFalse(permissions.autostartAcknowledged)
        assertFalse(permissions.allCriticalGranted)
    }

    @Test
    fun `when autostart is needed and acknowledged allCriticalGranted is true`() = runTest {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true
        )
        every { OemAutostartHelper.getAutostartIntent(any()) } returns mockk(relaxed = true)
        every { appSettingsRepository.getSetting("autostart_acknowledged") } returns flowOf("true")

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

        val permissions = viewModel.uiState.value.permissions
        assertTrue(permissions.hasAutostartIntent)
        assertTrue(permissions.autostartAcknowledged)
        assertTrue(permissions.allCriticalGranted)
    }
    
    @Test
    fun `onAutostartGranted saves setting and refreshes`() = runTest {
        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.onAutostartGranted()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { appSettingsRepository.setSetting("autostart_acknowledged", "true") }
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
