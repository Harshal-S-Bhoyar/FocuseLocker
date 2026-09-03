package com.focuslock.ui.session

import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.repository.UnlockRepository
import com.focuslock.domain.usecase.GetSessionRemainingTimeUseCase
import com.focuslock.domain.usecase.DeactivateSessionUseCase
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.model.UnlockRequest
import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.session.SessionProtectionStatus
import com.focuslock.domain.session.SessionProtectionStatusProvider
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
    private val deactivateSessionUseCase = mockk<DeactivateSessionUseCase>()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { getSessionRemainingTimeUseCase.invoke() } returns flowOf(-1L)
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
            unlockRepository,
            deactivateSessionUseCase
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

    @Test
    fun `refreshProtectionStatus replaces the cached protection snapshot`() = runTest {
        val refreshed = SessionProtectionStatus(
            vpnPermissionGranted = false,
            accessibilityEnabled = true,
            deviceAdminEnabled = false,
            batteryOptimizationIgnored = true
        )
        every { protectionStatusProvider.getStatus() } returnsMany listOf(
            SessionProtectionStatus(true, true, true, true),
            refreshed
        )

        val viewModel = ActiveSessionViewModel(
            getSessionRemainingTimeUseCase,
            protectionStatusProvider,
            sessionRepository,
            unlockRepository,
            deactivateSessionUseCase
        )

        viewModel.refreshProtectionStatus()

        assertEquals(refreshed, viewModel.protectionStatus.value)
    }

    @Test
    fun `sessionActive emits false when repository has no active session`() = runTest {
        val viewModel = ActiveSessionViewModel(
            getSessionRemainingTimeUseCase,
            protectionStatusProvider,
            sessionRepository,
            unlockRepository,
            deactivateSessionUseCase
        )
        val collectJob = launch { viewModel.sessionActive.collect {} }

        runCurrent()

        assertEquals(false, viewModel.sessionActive.value)
        collectJob.cancel()
    }

    @Test
    fun `activeUnlockInfo maps the latest request for the active session`() = runTest {
        val session = activeSession(7L)
        val request = UnlockRequest(
            id = 3L,
            sessionId = session.id,
            requestedAt = 100L,
            status = UnlockRequestStatus.COOLDOWN,
            otpAttempts = 1,
            cooldownEndsAt = 10_000L
        )
        every { sessionRepository.getActiveSession() } returns flowOf(session)
        every { unlockRepository.getLatestRequest(session.id) } returns flowOf(request)

        val viewModel = ActiveSessionViewModel(
            getSessionRemainingTimeUseCase,
            protectionStatusProvider,
            sessionRepository,
            unlockRepository,
            deactivateSessionUseCase
        )
        val collectJob = launch { viewModel.activeUnlockInfo.collect {} }

        runCurrent()

        assertEquals(UnlockRequestStatus.COOLDOWN, viewModel.activeUnlockInfo.value?.status)
        assertEquals(10_000L, viewModel.activeUnlockInfo.value?.cooldownEndsAt)
        collectJob.cancel()
    }

    @Test
    fun `completeCooldownAndKillSession deactivates the current session`() = runTest {
        val session = activeSession(11L)
        coEvery { sessionRepository.getActiveSessionSync() } returns session
        coEvery {
            deactivateSessionUseCase(session.id, SessionStatus.COMPLETED)
        } returns Result.success(Unit)

        val viewModel = ActiveSessionViewModel(
            getSessionRemainingTimeUseCase,
            protectionStatusProvider,
            sessionRepository,
            unlockRepository,
            deactivateSessionUseCase
        )

        viewModel.completeCooldownAndKillSession()
        runCurrent()

        coVerify(exactly = 1) {
            deactivateSessionUseCase(session.id, SessionStatus.COMPLETED)
        }
    }

    @Test
    fun `completeCooldownAndKillSession does nothing without a current session`() = runTest {
        coEvery { sessionRepository.getActiveSessionSync() } returns null

        val viewModel = ActiveSessionViewModel(
            getSessionRemainingTimeUseCase,
            protectionStatusProvider,
            sessionRepository,
            unlockRepository,
            deactivateSessionUseCase
        )

        viewModel.completeCooldownAndKillSession()
        runCurrent()

        coVerify(exactly = 0) { deactivateSessionUseCase(any(), any()) }
    }

    private fun activeSession(id: Long) = Session(
        id = id,
        status = SessionStatus.ACTIVE,
        startedAt = 0L,
        expiresAt = Long.MAX_VALUE,
        unlockedEarly = false,
        unlockRequestedAt = null,
        createdAt = 0L
    )
}
