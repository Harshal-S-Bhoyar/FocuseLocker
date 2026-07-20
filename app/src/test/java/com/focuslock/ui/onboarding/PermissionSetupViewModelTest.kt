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
import com.focuslock.util.OemBatteryOptimizationHelper
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
        mockkObject(OemBatteryOptimizationHelper)
        every { OemAutostartHelper.getAutostartIntentsList(any()) } returns emptyList()
        every { OemBatteryOptimizationHelper.getBatteryOptimizationIntentsList(any()) } returns emptyList()
        every { permissionStatusProvider.getStatus() } returns permissionStatus()
        io.mockk.coEvery { appSettingsRepository.setSetting(any(), any()) } returns Unit
        every { appSettingsRepository.getSetting(any()) } returns flowOf(null)
        every { appSettingsRepository.getSetting("battery_optimization_acknowledged") } returns flowOf("true")
        every { appSettingsRepository.getSetting("autostart_acknowledged") } returns flowOf("true")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(OemAutostartHelper)
        unmockkObject(OemBatteryOptimizationHelper)
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
    fun `refreshPermissions correctly maps active permissions`() = runTest(testDispatcher) {
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
    fun `when all critical permissions are granted allCriticalGranted is true`() = runTest(testDispatcher) {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true,
            notificationGranted = true
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
    fun `onContinueClicked does nothing when critical permissions are missing`() = runTest(testDispatcher) {
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
    fun `onContinueClicked saves setting and navigates when critical permissions are granted`() = runTest(testDispatcher) {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true,
            notificationGranted = true
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
    fun `onNavigationConsumed resets navigation flag`() = runTest(testDispatcher) {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true,
            notificationGranted = true
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
    fun `onContinueClicked succeeds even when batteryOptimizationIgnored is missing`() = runTest(testDispatcher) {
        // ponytail: Battery optimization is optional/recommended, not a blocking gate.
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = false,
            notificationGranted = true
        )

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

        val permissions = viewModel.uiState.value.permissions
        assertTrue(permissions.allCriticalGranted)

        viewModel.onContinueClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.navigateToTrustedContact)
        coVerify(exactly = 1) { appSettingsRepository.setSetting("permissions_granted", "true") }
    }

    @Test
    fun `refreshPermissions maps autostart intent availability`() = runTest(testDispatcher) {
        every { OemAutostartHelper.getAutostartIntentsList(any()) } returns listOf(mockk(relaxed = true))
        every { appSettingsRepository.getSetting("autostart_acknowledged") } returns flowOf("true")

        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

        val permissions = viewModel.uiState.value.permissions
        assertTrue(permissions.hasAutostartIntent)
        assertTrue(permissions.autostartAcknowledged)
    }

    @Test
    fun `when autostart is needed but not acknowledged allCriticalGranted is false`() = runTest(testDispatcher) {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true,
            notificationGranted = true
        )
        every { OemAutostartHelper.getAutostartIntentsList(any()) } returns listOf(mockk(relaxed = true))
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
    fun `when autostart is needed and acknowledged allCriticalGranted is true`() = runTest(testDispatcher) {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true,
            deviceAdminGranted = true,
            overlayGranted = true,
            vpnGranted = true,
            batteryOptimizationIgnored = true,
            notificationGranted = true
        )
        every { OemAutostartHelper.getAutostartIntentsList(any()) } returns listOf(mockk(relaxed = true))
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
    fun `onAutostartGranted saves setting and refreshes`() = runTest(testDispatcher) {
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

    @Test
    fun `refreshPermissionsDelayed calls refreshPermissions after delay`() = runTest(testDispatcher) {
        every { permissionStatusProvider.getStatus() } returns permissionStatus(
            accessibilityGranted = true
        )
        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        
        viewModel.refreshPermissionsDelayed(1000L)
        
        // Before delay, state shouldn't change
        testDispatcher.scheduler.advanceTimeBy(500L)
        assertFalse(viewModel.uiState.value.permissions.accessibilityGranted)
        
        // After delay, state should change
        testDispatcher.scheduler.advanceTimeBy(501L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.permissions.accessibilityGranted)
    }

    @Test
    fun `refreshPermissions does not cancel existing delayJob`() = runTest(testDispatcher) {
        var statusCallCount = 0
        every { permissionStatusProvider.getStatus() } answers {
            statusCallCount++
            permissionStatus(accessibilityGranted = true)
        }
        
        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        
        // Call delayed refresh
        viewModel.refreshPermissionsDelayed(1000L)
        testDispatcher.scheduler.advanceTimeBy(500L)
        
        // Call immediate refresh which should NOT cancel the delayed job
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Status should be called 4 times: 1 time from immediate refresh before cancellation, 3 times from delayed refresh
        assertEquals(4, statusCallCount)
    }

    @Test
    fun `multiple refreshPermissionsDelayed only execute the last one`() = runTest(testDispatcher) {
        var statusCallCount = 0
        every { permissionStatusProvider.getStatus() } answers {
            statusCallCount++
            permissionStatus(accessibilityGranted = true)
        }
        
        viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        
        // Call multiple delayed refreshes
        viewModel.refreshPermissionsDelayed(1000L)
        testDispatcher.scheduler.advanceTimeBy(500L)
        
        viewModel.refreshPermissionsDelayed(1000L)
        testDispatcher.scheduler.advanceTimeBy(500L)

        viewModel.refreshPermissionsDelayed(1000L)
        
        // Advance time to complete the last delay
        testDispatcher.scheduler.advanceTimeBy(1001L)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Status should be called 3 times (from the retry loop of the single delayed job)
        assertEquals(3, statusCallCount)
    }
}
