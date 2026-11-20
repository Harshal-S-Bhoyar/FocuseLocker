package com.focuslock.ui.onboarding

import com.focuslock.domain.permission.AppPermissionStatus
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.domain.usecase.CheckOnboardingStatusUseCase
import com.focuslock.domain.usecase.InitializeAppUseCase
import com.focuslock.domain.usecase.OnboardingStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One slow binder probe used to report every permission as missing and route the user into the
 * permission-repair flow, without ever being retried (`OnboardingViewModel.probePermissions`).
 * These tests pin the retry, and that the real probe result is what routing uses.
 *
 * The slow probe is modelled as a blocking `getStatus()` on a real single-thread executor, parked
 * on a latch until the test releases it. The probe's own 8s ceiling runs on the virtual clock, so
 * the timeout fires while the worker is still parked: the attempt can only end the way a timed-out
 * probe ends, and the retry then reaches the provider. Blocking the test thread instead would stop
 * that virtual clock, which is why the slow call needs a thread of its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingProbeRetryTest {

    private val testDispatcher = StandardTestDispatcher()
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

    /** Counts consultations and grants everything. */
    private class RecordingPermissionProvider : AppPermissionStatusProvider {
        var probes = 0
        override fun getStatus(): AppPermissionStatus {
            probes++
            return AppPermissionStatus(true, true, true, true, true, true)
        }
    }

    @Test
    fun `a probe that times out is retried before permissions are treated as missing`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = true,
            hasVerifiedContact = true,
            hasActiveSession = false
        )

        val ioExecutor = Executors.newSingleThreadExecutor()
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val firstProbeEntered = CountDownLatch(1)
        val releaseFirstProbe = CountDownLatch(1)
        val probes = AtomicInteger(0)
        val provider = object : AppPermissionStatusProvider {
            override fun getStatus(): AppPermissionStatus {
                if (probes.incrementAndGet() == 1) {
                    firstProbeEntered.countDown()
                    releaseFirstProbe.await()
                }
                return AppPermissionStatus(true, true, true, true, true, true)
            }
        }

        try {
            val viewModel = OnboardingViewModel(
                initializeAppUseCase,
                checkOnboardingStatusUseCase,
                provider,
                ioDispatcher
            )

            // Start init up to the blocked probe; its 8s ceiling now waits on the virtual clock.
            runCurrent()
            assertTrue(
                "the first probe must reach the provider before it can time out",
                firstProbeEntered.await(5, TimeUnit.SECONDS)
            )

            // Fire the ceiling while the probe is still parked: the attempt times out.
            advanceUntilIdle()

            // Answer the parked attempt. By now the timeout, not the answer, decides its outcome.
            releaseFirstProbe.countDown()

            // Deliver the timeout and run the retry. runCurrent never advances the virtual clock,
            // so the retry's own ceiling cannot fire; the real sleep lets the worker post results.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (viewModel.uiState.value.isLoading && System.nanoTime() < deadline) {
                runCurrent()
                Thread.sleep(10)
            }

            assertEquals(
                "a timed-out attempt must be followed by exactly one retry consultation",
                2, probes.get()
            )
            assertEquals(
                "routing must use the real permission state, not the degraded one",
                OnboardingEvent.NavigateToCategorySelection,
                viewModel.uiState.value.event
            )
            assertNull("a retried probe is not a startup failure", viewModel.uiState.value.error)
        } finally {
            ioDispatcher.close()
        }
    }

    @Test
    fun `a probe that answers promptly is consulted once and not retried`() = runTest {
        coEvery { checkOnboardingStatusUseCase() } returns OnboardingStatus(
            isOnboardingComplete = true,
            hasVerifiedContact = true,
            hasActiveSession = false
        )
        val provider = RecordingPermissionProvider()

        val viewModel = OnboardingViewModel(
            initializeAppUseCase,
            checkOnboardingStatusUseCase,
            provider,
            testDispatcher
        )
        advanceUntilIdle()

        assertEquals("a prompt probe must not be probed twice", 1, provider.probes)
        assertEquals(
            OnboardingEvent.NavigateToCategorySelection,
            viewModel.uiState.value.event
        )
    }
}
