package com.focuslock.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.domain.permission.AppPermissionStatus
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.ui.onboarding.PermissionSetupViewModel
import com.focuslock.util.OemAutostartHelper
import com.focuslock.util.OemBatteryOptimizationHelper
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PermissionNavigationIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private val permissionStatusProvider: AppPermissionStatusProvider = mockk()
    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        if (Timber.treeCount == 0) {
            Timber.plant(TestLogTree())
        }
        context = ApplicationProvider.getApplicationContext()
        
        mockkObject(OemAutostartHelper)
        mockkObject(OemBatteryOptimizationHelper)
        every { OemAutostartHelper.getAutostartIntentsList(any()) } returns emptyList()
        every { OemBatteryOptimizationHelper.getBatteryOptimizationIntentsList(any()) } returns emptyList()
        coEvery { appSettingsRepository.setSetting(any(), any()) } returns Unit
        every { appSettingsRepository.getSetting(any()) } returns flowOf(null)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `navigating to settings and back updates permissions and resolves correctly without multiple delay loops`() = runTest(testDispatcher) {
        var statusCallCount = 0
        var status = AppPermissionStatus(
            accessibilityGranted = false,
            deviceAdminGranted = true,
            overlayGranted = true,
            batteryOptimizationIgnored = true,
            vpnGranted = true,
            notificationGranted = true
        )
        every { permissionStatusProvider.getStatus() } answers { 
            statusCallCount++
            status 
        }

        val viewModel = PermissionSetupViewModel(permissionStatusProvider, appSettingsRepository, context)
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.permissions.allCriticalGranted)
        
        val baselineCallCount = statusCallCount

        // Simulate user navigating to settings and the app registering a delayed check
        viewModel.refreshPermissionsDelayed(1000L)
        testDispatcher.scheduler.advanceTimeBy(500L) // Halfway through delay

        // Simulate returning to app, the lifecycle onResume triggers immediate refresh
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

        // Ensure delayed check was cancelled and replaced by immediate check (4 calls total due to delay overlaps)
        assertEquals(baselineCallCount + 4, statusCallCount)
        assertFalse(viewModel.uiState.value.permissions.allCriticalGranted)

        // User goes again
        viewModel.refreshPermissionsDelayed(1000L)
        testDispatcher.scheduler.advanceTimeBy(500L)

        // This time they granted it
        status = status.copy(accessibilityGranted = true)

        // They return to app, onResume triggers immediate refresh
        viewModel.refreshPermissions()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(baselineCallCount + 6, statusCallCount)
        assertTrue(viewModel.uiState.value.permissions.allCriticalGranted)

        // Now, if they continue, it navigates
        viewModel.onContinueClicked()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.navigateToTrustedContact)
        
        // After they navigate, the consumed event is dispatched
        viewModel.onNavigationConsumed()
        assertFalse(viewModel.uiState.value.navigateToTrustedContact)
    }
}
