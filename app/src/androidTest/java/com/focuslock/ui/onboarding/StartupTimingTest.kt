package com.focuslock.ui.onboarding

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.BlockingRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.repository.TrustedContactRepository
import com.focuslock.domain.permission.AppPermissionStatus
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.domain.repository.BackgroundSyncScheduler
import com.focuslock.domain.usecase.CheckOnboardingStatusUseCase
import com.focuslock.domain.usecase.CleanupStaleSessionUseCase
import com.focuslock.domain.usecase.InitializeAppUseCase
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Warm-start timing: with the seed flag already set, onboarding init must leave
 * loading quickly. Uses real Room (in-memory) plus real use cases; only the
 * permission probe and stale cleanup are faked.
 */
@RunWith(AndroidJUnit4::class)
class StartupTimingTest {

    private lateinit var context: Context
    private lateinit var database: FocusDatabase
    private lateinit var viewModel: OnboardingViewModel

    private val granted = AppPermissionStatus(true, true, true, true, true, true)
    private val permissionStatusProvider = object : AppPermissionStatusProvider {
        override fun getStatus(): AppPermissionStatus = granted
    }
    private val backgroundSyncScheduler = object : BackgroundSyncScheduler {
        override fun schedulePeriodicSync() = Unit
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java)
            .allowMainThreadQueries().build()
        val sessionRepository = SessionRepository(database.sessionDao())
        val appSettingsRepository = AppSettingsRepository(database.appSettingDao())
        val blockingRepository = BlockingRepository(
            database.blockedKeywordDao(),
            database.blockedCategoryDao(),
            database.blockedDomainDao(),
            context
        )
        val cleanupStaleSessionUseCase = CleanupStaleSessionUseCase(
            sessionRepository,
            mockk(relaxed = true)
        )
        val initializeAppUseCase = InitializeAppUseCase(
            appSettingsRepository,
            blockingRepository,
            cleanupStaleSessionUseCase,
            backgroundSyncScheduler
        )
        val checkOnboardingStatusUseCase = CheckOnboardingStatusUseCase(
            appSettingsRepository,
            TrustedContactRepository(
                database.trustedContactDao(),
                mockk<FocusLockApi>(relaxed = true)
            ),
            sessionRepository
        )
        viewModel = OnboardingViewModel(
            initializeAppUseCase,
            checkOnboardingStatusUseCase,
            permissionStatusProvider,
            kotlinx.coroutines.Dispatchers.Main
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun warmStartLeavesLoadingWithinTwoSeconds() {
        val deadline = SystemClock.uptimeMillis() + 2_000L
        while (viewModel.uiState.value.isLoading && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L)
        }

        assertFalse("warm start must leave loading within 2s", viewModel.uiState.value.isLoading)
        assertTrue(
            "warm start must finish inside the 2s budget",
            SystemClock.uptimeMillis() < deadline
        )
    }
}
