package com.focuslock.ui.session

import app.cash.turbine.test
import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.repository.UnlockRepository
import com.focuslock.domain.usecase.RequestUnlockUseCase
import com.focuslock.domain.usecase.StartEmergencyBypassUseCase
import com.focuslock.domain.usecase.VerifyUnlockOtpUseCase
import com.focuslock.domain.usecase.VerifyUnlockResult
import com.focuslock.domain.usecase.GetSessionRemainingTimeUseCase
import com.focuslock.domain.usecase.DeactivateSessionUseCase
import com.focuslock.domain.repository.TrustedContactRepository
import com.focuslock.domain.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var sessionRepository: SessionRepository
    private lateinit var unlockRepository: UnlockRepository
    private lateinit var requestUnlockUseCase: RequestUnlockUseCase
    private lateinit var verifyUnlockOtpUseCase: VerifyUnlockOtpUseCase
    private lateinit var startEmergencyBypassUseCase: StartEmergencyBypassUseCase
    private lateinit var deactivateSessionUseCase: DeactivateSessionUseCase

    private lateinit var viewModel: UnlockViewModel
    private val requestFlow = kotlinx.coroutines.flow.MutableSharedFlow<com.focuslock.domain.model.UnlockRequest?>(replay = 1)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        sessionRepository = mockk()
        unlockRepository = mockk()
        requestUnlockUseCase = mockk()
        verifyUnlockOtpUseCase = mockk()
        startEmergencyBypassUseCase = mockk()
        deactivateSessionUseCase = mockk()
        val trustedContactRepository: TrustedContactRepository = mockk()
        val blockingRepository: BlockingRepository = mockk()
        val getSessionRemainingTimeUseCase: GetSessionRemainingTimeUseCase = mockk()

        coEvery { trustedContactRepository.getContactSync() } returns com.focuslock.domain.model.TrustedContact(
            id = 1L,
            email = "+1234567890",
            isVerified = true,
            verifiedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        coEvery { blockingRepository.getActiveCategoryKeys() } returns emptyList()
        coEvery { blockingRepository.getActiveKeywords() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { getSessionRemainingTimeUseCase.invoke() } returns kotlinx.coroutines.flow.flowOf(3600000L)

        coEvery { sessionRepository.getActiveSession() } returns kotlinx.coroutines.flow.flowOf(mockk {
            coEvery { id } returns 1L
            coEvery { expiresAt } returns System.currentTimeMillis() + 3600000L
            coEvery { status } returns com.focuslock.domain.model.SessionStatus.ACTIVE
        })

        requestFlow.tryEmit(null)
        coEvery { unlockRepository.getLatestRequest(any()) } returns requestFlow

        // Default mock responses
        coEvery { sessionRepository.getActiveSessionSync() } returns mockk {
            coEvery { id } returns 1L
            coEvery { expiresAt } returns System.currentTimeMillis() + 3600000L
            coEvery { status } returns com.focuslock.domain.model.SessionStatus.ACTIVE
        }
        coEvery { unlockRepository.getLatestRequestSync(any()) } returns null

        viewModel = UnlockViewModel(
            sessionRepository,
            unlockRepository,
            trustedContactRepository,
            blockingRepository,
            requestUnlockUseCase,
            verifyUnlockOtpUseCase,
            startEmergencyBypassUseCase,
            getSessionRemainingTimeUseCase,
            deactivateSessionUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            val item = awaitItem()
            assertEquals(UnlockUiState.Idle, item)
        }
    }

    @Test
    fun `requestUnlock success transitions to WaitingForOtp`() = runTest {
        coEvery { requestUnlockUseCase.invoke(any(), any(), any(), any(), any()) } returns Result.success(1L)

        viewModel.requestUnlock()
        
        requestFlow.emit(mockk {
            coEvery { id } returns 1L
            coEvery { status } returns UnlockRequestStatus.OTP_SENT
            coEvery { otpAttempts } returns 0
            coEvery { requestedAt } returns System.currentTimeMillis()
        })
        
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val item = awaitItem()
            assertTrue("Expected WaitingForOtp but was $item", item is UnlockUiState.WaitingForOtp)
        }
    }

    @Test
    fun `requestUnlock failure transitions to Error`() = runTest {
        coEvery { requestUnlockUseCase.invoke(any(), any(), any(), any(), any()) } returns Result.failure(Exception("Failed"))

        viewModel.requestUnlock()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val item = awaitItem()
            assertTrue("Expected Error but was $item", item is UnlockUiState.Error)
            assertEquals("Failed", (item as UnlockUiState.Error).message)
        }
    }

    @Test
    fun `verifyOtp with valid code transitions to CooldownActive`() = runTest {
        // Setup initial state
        coEvery { requestUnlockUseCase.invoke(any(), any(), any(), any(), any()) } returns Result.success(1L)
        viewModel.requestUnlock()
        
        requestFlow.emit(mockk {
            coEvery { id } returns 1L
            coEvery { status } returns UnlockRequestStatus.OTP_SENT
            coEvery { otpAttempts } returns 0
            coEvery { requestedAt } returns System.currentTimeMillis()
        })
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { verifyUnlockOtpUseCase.invoke(1L, "+1234567890", "123456") } returns VerifyUnlockResult.Valid
        coEvery { unlockRepository.getLatestRequestSync(any()) } returns mockk {
            coEvery { status } returns UnlockRequestStatus.COOLDOWN
            coEvery { cooldownEndsAt } returns System.currentTimeMillis() + 600000L
        }

        viewModel.verifyOtp("123456")
        
        requestFlow.emit(mockk {
            coEvery { id } returns 1L
            coEvery { status } returns UnlockRequestStatus.COOLDOWN
            coEvery { cooldownEndsAt } returns System.currentTimeMillis() + 600000L
        })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val item = awaitItem()
            assertTrue("Expected CooldownActive but was $item", item is UnlockUiState.CooldownActive)
        }
    }

    @Test
    fun `startEmergencyBypass success transitions to EmergencyActive`() = runTest {
        coEvery { startEmergencyBypassUseCase.invoke(1L) } returns Result.success(Unit)
        coEvery { unlockRepository.getLatestRequestSync(any()) } returns mockk {
            coEvery { status } returns UnlockRequestStatus.EMERGENCY
            coEvery { cooldownEndsAt } returns System.currentTimeMillis() + 86400000L
        }

        viewModel.startEmergencyBypass()
        
        requestFlow.emit(mockk {
            coEvery { id } returns 1L
            coEvery { status } returns UnlockRequestStatus.EMERGENCY
            coEvery { cooldownEndsAt } returns System.currentTimeMillis() + 86400000L
        })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val item = awaitItem()
            assertTrue("Expected EmergencyBypassActive but was $item", item is UnlockUiState.EmergencyBypassActive)
        }
    }
}
