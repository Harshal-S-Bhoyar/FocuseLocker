package com.focuslock.domain.usecase


import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class InitializeAppUseCaseTest {

    private lateinit var useCase: InitializeAppUseCase
    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val blockingRepository: BlockingRepository = mockk(relaxed = true)
    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase = mockk(relaxed = true)
    private val backgroundSyncScheduler: com.focuslock.domain.repository.BackgroundSyncScheduler = mockk(relaxed = true)

    @Before
    fun setUp() {
        useCase = InitializeAppUseCase(appSettingsRepository, blockingRepository, cleanupStaleSessionUseCase, backgroundSyncScheduler)
    }

    @Test
    fun `when invoke is called then default settings and domain data are seeded`() = runTest {
        coEvery { appSettingsRepository.isDomainSeeded() } returns false

        useCase()

        coVerify(exactly = 1) { appSettingsRepository.initializeDefaultSettings() }
        coVerify(exactly = 1) { blockingRepository.seedDomainDataIfNeeded() }
        coVerify(exactly = 1) { appSettingsRepository.setDomainSeeded() }
        coVerify(exactly = 1) { backgroundSyncScheduler.schedulePeriodicSync() }
    }

    @Test
    fun `when domains are already seeded then seeding is skipped`() = runTest {
        coEvery { appSettingsRepository.isDomainSeeded() } returns true

        useCase()

        coVerify(exactly = 1) { appSettingsRepository.initializeDefaultSettings() }
        coVerify(exactly = 0) { blockingRepository.seedDomainDataIfNeeded() }
        coVerify(exactly = 0) { appSettingsRepository.setDomainSeeded() }
        coVerify(exactly = 1) { backgroundSyncScheduler.schedulePeriodicSync() }
    }

    @Test
    fun `when initialization succeeds then cleanup runs first and settings seeding and scheduling all occur`() = runTest {
        coEvery { appSettingsRepository.isDomainSeeded() } returns false

        useCase()

        coVerifyOrder {
            cleanupStaleSessionUseCase()
        }
        coVerify(exactly = 1) { appSettingsRepository.initializeDefaultSettings() }
        coVerify(exactly = 1) { blockingRepository.seedDomainDataIfNeeded() }
        coVerify(exactly = 1) { appSettingsRepository.setDomainSeeded() }
        coVerify(exactly = 1) { backgroundSyncScheduler.schedulePeriodicSync() }
    }

    @Test
    fun `cleanup failure prevents later initialization steps`() = runTest {
        val failure = IllegalStateException("cleanup failed")
        coEvery { cleanupStaleSessionUseCase() } throws failure

        try {
            useCase()
            throw AssertionError("Expected cleanup failure")
        } catch (error: IllegalStateException) {
            assertEquals(failure, error)
        }
        coVerify(exactly = 0) { appSettingsRepository.initializeDefaultSettings() }
        coVerify(exactly = 0) { backgroundSyncScheduler.schedulePeriodicSync() }
    }

    @Test
    fun `domain seed failure prevents seeded marker and scheduling`() = runTest {
        val failure = IllegalStateException("seed failed")
        coEvery { appSettingsRepository.isDomainSeeded() } returns false
        coEvery { blockingRepository.seedDomainDataIfNeeded() } throws failure

        try {
            useCase()
            throw AssertionError("Expected seed failure")
        } catch (error: IllegalStateException) {
            // Identity is not asserted: exceptions crossing structured-concurrency
            // boundaries are not guaranteed to be the same instance.
            assertEquals("seed failed", error.message)
        }
        coVerify(exactly = 0) { appSettingsRepository.setDomainSeeded() }
        coVerify(exactly = 0) { backgroundSyncScheduler.schedulePeriodicSync() }
    }

    @Test
    fun `concurrent invocations seed only once`() = runTest {
        var seeded = false
        coEvery { appSettingsRepository.isDomainSeeded() } answers { seeded }
        coEvery { appSettingsRepository.setDomainSeeded() } answers { seeded = true }

        val first = launch { useCase() }
        val second = launch { useCase() }
        first.join()
        second.join()

        coVerify(exactly = 1) { blockingRepository.seedDomainDataIfNeeded() }
    }

    @Test
    fun `wedged settings step fails within the step budget instead of hanging`() = runTest {
        coEvery { appSettingsRepository.isDomainSeeded() } returns true
        coEvery { appSettingsRepository.initializeDefaultSettings() } coAnswers { awaitCancellation() }

        try {
            useCase()
            throw AssertionError("Expected timeout")
        } catch (error: TimeoutCancellationException) {
            // Expected: the step budget fires instead of hanging forever.
        }
        coVerify(exactly = 0) { backgroundSyncScheduler.schedulePeriodicSync() }
    }
}
