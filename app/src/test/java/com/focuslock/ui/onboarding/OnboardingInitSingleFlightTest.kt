package com.focuslock.ui.onboarding

import com.focuslock.domain.permission.AppPermissionStatus
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.domain.usecase.CheckOnboardingStatusUseCase
import com.focuslock.domain.usecase.InitializeAppUseCase
import com.focuslock.domain.usecase.OnboardingStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingInitSingleFlightTest {

    private val testDispatcher = StandardTestDispatcher()
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
    fun `back-to-back retries run a single sequence`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { initializeAppUseCase() } coAnswers { gate.await() }
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = false,
            hasVerifiedContact = false,
            hasActiveSession = false
        )
        coEvery { permissionStatusProvider.getStatus() } returns AppPermissionStatus(false, false, false, false, false, false)

        val viewModel = OnboardingViewModel(
            initializeAppUseCase, checkOnboardingStatusUseCase, permissionStatusProvider, testDispatcher
        )
        testDispatcher.scheduler.runCurrent()
        viewModel.retry()
        viewModel.retry()
        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // First run entered and was cancelled, second never ran, third completed.
        coVerify(exactly = 2) { initializeAppUseCase() }
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.event)
    }
}
