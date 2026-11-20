package com.focuslock.ui.setup

import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.domain.usecase.HasVerifiedContactUseCase
import com.focuslock.domain.usecase.ScheduleEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleContactLoadedTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `contact state unknown until load completes`() = runTest(testDispatcher) {
        val repository: ScheduleRepository = mockk(relaxed = true)
        val hasVerifiedContactUseCase: HasVerifiedContactUseCase = mockk()
        coEvery { hasVerifiedContactUseCase() } returns true
        every { repository.getWindows() } returns MutableStateFlow(emptyList())
        every { repository.isScheduleEnabled() } returns MutableStateFlow(false)

        val viewModel = ScheduleEditorViewModel(
            repository,
            ScheduleEngine(),
            hasVerifiedContactUseCase,
            mockk<AppSettingsRepository>(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )

        assertFalse(viewModel.isContactLoaded.value)
        advanceUntilIdle()
        assertTrue(viewModel.isContactLoaded.value)
        assertTrue(viewModel.hasVerifiedContact.value)
    }
}
