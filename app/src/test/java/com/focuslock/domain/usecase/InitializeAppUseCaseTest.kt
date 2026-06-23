package com.focuslock.domain.usecase

import android.content.Context
import androidx.work.WorkManager
import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class InitializeAppUseCaseTest {

    private lateinit var useCase: InitializeAppUseCase
    private val appSettingsRepository: AppSettingsRepository = mockk(relaxed = true)
    private val blockingRepository: BlockingRepository = mockk(relaxed = true)
    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        useCase = InitializeAppUseCase(appSettingsRepository, blockingRepository, cleanupStaleSessionUseCase, context)
    }

    @Test
    fun `when invoke is called then default settings and domain data are seeded`() = runTest {
        useCase()

        coVerify(exactly = 1) { appSettingsRepository.initializeDefaultSettings() }
        coVerify(exactly = 1) { blockingRepository.seedDomainDataIfNeeded() }
    }
}
