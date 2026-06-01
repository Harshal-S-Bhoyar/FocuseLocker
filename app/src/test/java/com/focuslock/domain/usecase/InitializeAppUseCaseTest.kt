package com.focuslock.domain.usecase

import com.focuslock.domain.repository.AppSettingsRepository
import com.focuslock.domain.repository.BlockingRepository
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

    @Before
    fun setUp() {
        useCase = InitializeAppUseCase(appSettingsRepository, blockingRepository)
    }

    @Test
    fun `when invoke is called then default settings and domain data are seeded`() = runTest {
        useCase()

        coVerify(exactly = 1) { appSettingsRepository.initializeDefaultSettings() }
        coVerify(exactly = 1) { blockingRepository.seedDomainDataIfNeeded() }
    }
}
