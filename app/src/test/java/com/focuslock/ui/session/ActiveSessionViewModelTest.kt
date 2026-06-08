package com.focuslock.ui.session

import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.repository.UnlockRepository
import com.focuslock.domain.usecase.GetSessionRemainingTimeUseCase
import com.focuslock.domain.session.SessionProtectionStatus
import com.focuslock.domain.session.SessionProtectionStatusProvider
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
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelTest {

    private val getSessionRemainingTimeUseCase = mockk<GetSessionRemainingTimeUseCase>()
    private val protectionStatusProvider = mockk<SessionProtectionStatusProvider>()
    private val sessionRepository = mockk<SessionRepository>()
    private val unlockRepository = mockk<UnlockRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { protectionStatusProvider.getStatus() } returns SessionProtectionStatus(
            vpnPermissionGranted = true,
            accessibilityEnabled = true,
            deviceAdminEnabled = true,
            batteryOptimizationIgnored = true
        )
        every { sessionRepository.getActiveSession() } returns flowOf(null)
        every { unlockRepository.getLatestRequest(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `remainingTimeMillis emits values from usecase`() = runTest {
        // Arrange
        every { getSessionRemainingTimeUseCase.invoke() } returns flowOf(5000L, 4000L)

        // Act
        val viewModel = ActiveSessionViewModel(
            getSessionRemainingTimeUseCase, 
            protectionStatusProvider,
            sessionRepository,
            unlockRepository
        )

        // Start collecting so SharingStarted.WhileSubscribed triggers
        val collectJob = launch {
            viewModel.remainingTimeMillis.collect {}
        }
        
        runCurrent()

        // Assert
        assertEquals(4000L, viewModel.remainingTimeMillis.value)
        collectJob.cancel()
    }
}
