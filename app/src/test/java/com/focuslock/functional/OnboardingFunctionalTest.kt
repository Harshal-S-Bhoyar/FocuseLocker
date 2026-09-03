package com.focuslock.functional

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.repository.TrustedContactRepository
import com.focuslock.domain.usecase.CheckOnboardingStatusUseCase
import com.focuslock.data.remote.api.FocusLockApi
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class OnboardingFunctionalTest {

    private lateinit var db: FocusDatabase
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var trustedContactRepository: TrustedContactRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var checkOnboardingStatusUseCase: CheckOnboardingStatusUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val mockApi = mockk<FocusLockApi>(relaxed = true)

        appSettingsRepository = AppSettingsRepository(db.appSettingDao())
        trustedContactRepository = TrustedContactRepository(db.trustedContactDao(), mockApi)
        sessionRepository = SessionRepository(db.sessionDao())

        checkOnboardingStatusUseCase = CheckOnboardingStatusUseCase(
            appSettingsRepository = appSettingsRepository,
            trustedContactRepository = trustedContactRepository,
            sessionRepository = sessionRepository
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `verify onboarding flow success criteria`() = runBlocking {
        // 1. Initially, onboarding is not complete
        var status = checkOnboardingStatusUseCase()
        assertFalse("Onboarding should not be complete initially", status.isOnboardingComplete)
        assertFalse("Should not have verified contact initially", status.hasVerifiedContact)
        assertFalse("Should not have active session initially", status.hasActiveSession)

        // 2. Initialize default settings
        appSettingsRepository.initializeDefaultSettings()
        status = checkOnboardingStatusUseCase()
        assertFalse("Onboarding should still be incomplete after defaults", status.isOnboardingComplete)

        // 3. User goes through onboarding and it completes
        appSettingsRepository.setOnboardingComplete()
        
        // 4. Verify the status reflects completion
        status = checkOnboardingStatusUseCase()
        assertTrue("Onboarding should be complete after saving", status.isOnboardingComplete)
    }

    @Test
    fun `default settings initialization does not overwrite completed onboarding`() = runBlocking {
        appSettingsRepository.initializeDefaultSettings()
        appSettingsRepository.setOnboardingComplete()
        appSettingsRepository.initializeDefaultSettings()

        assertTrue(appSettingsRepository.isOnboardingComplete())
        assertTrue(checkOnboardingStatusUseCase().isOnboardingComplete)
    }

    @Test
    fun `unverified contact is excluded until it is marked verified`() = runBlocking {
        val contactId = trustedContactRepository.saveContact("guardian@example.com")

        assertFalse(trustedContactRepository.hasVerifiedContact())
        assertFalse(checkOnboardingStatusUseCase().hasVerifiedContact)

        trustedContactRepository.markVerified(contactId)

        assertTrue(trustedContactRepository.hasVerifiedContact())
        assertTrue(checkOnboardingStatusUseCase().hasVerifiedContact)
    }

    @Test
    fun `future active session is reflected in onboarding status`() = runBlocking {
        sessionRepository.startSession(
            expiresAt = System.currentTimeMillis() + 60_000L,
            categoryKeys = listOf("SOCIAL_MEDIA")
        )

        val status = checkOnboardingStatusUseCase()

        assertTrue(status.hasActiveSession)
    }

    @Test
    fun `expired active session is not treated as active`() = runBlocking {
        sessionRepository.startSession(
            expiresAt = System.currentTimeMillis() - 1_000L,
            categoryKeys = listOf("SOCIAL_MEDIA")
        )

        val status = checkOnboardingStatusUseCase()

        assertFalse(status.hasActiveSession)
    }
}
