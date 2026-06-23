package com.focuslock.domain.usecase

import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.repository.TrustedContactRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CheckOnboardingStatusUseCaseTest {

    private lateinit var useCase: CheckOnboardingStatusUseCase
    private val appSettingsRepository: AppSettingsRepository = mockk()
    private val trustedContactRepository: TrustedContactRepository = mockk()
    private val sessionRepository: SessionRepository = mockk()

    @Before
    fun setUp() {
        useCase = CheckOnboardingStatusUseCase(
            appSettingsRepository,
            trustedContactRepository,
            sessionRepository
        )
    }

    @Test
    fun `when all status items are true returns OnboardingStatus with all true`() = runTest {
        coEvery { appSettingsRepository.isOnboardingComplete() } returns true
        coEvery { trustedContactRepository.hasVerifiedContact() } returns true
        coEvery { sessionRepository.hasActiveSession() } returns true

        val status = useCase()

        assertTrue(status.isOnboardingComplete)
        assertTrue(status.hasVerifiedContact)
        assertTrue(status.hasActiveSession)
    }

    @Test
    fun `when all status items are false returns OnboardingStatus with all false`() = runTest {
        coEvery { appSettingsRepository.isOnboardingComplete() } returns false
        coEvery { trustedContactRepository.hasVerifiedContact() } returns false
        coEvery { sessionRepository.hasActiveSession() } returns false

        val status = useCase()

        assertFalse(status.isOnboardingComplete)
        assertFalse(status.hasVerifiedContact)
        assertFalse(status.hasActiveSession)
    }

    @Test
    fun `when some status items are true and others false returns correct status combinations`() = runTest {
        coEvery { appSettingsRepository.isOnboardingComplete() } returns true
        coEvery { trustedContactRepository.hasVerifiedContact() } returns false
        coEvery { sessionRepository.hasActiveSession() } returns true

        val status = useCase()

        assertTrue(status.isOnboardingComplete)
        assertFalse(status.hasVerifiedContact)
        assertTrue(status.hasActiveSession)
    }
}
