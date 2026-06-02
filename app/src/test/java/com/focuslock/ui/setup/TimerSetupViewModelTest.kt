package com.focuslock.ui.setup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.focuslock.domain.usecase.HasVerifiedContactUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class TimerSetupViewModelTest {

    private lateinit var viewModel: TimerSetupViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var hasVerifiedContactUseCase: HasVerifiedContactUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        hasVerifiedContactUseCase = mockk()
        coEvery { hasVerifiedContactUseCase() } returns true
        viewModel = TimerSetupViewModel(hasVerifiedContactUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is valid with 15 minutes`() {
        assertEquals(0, viewModel.hours.value)
        assertEquals(15, viewModel.minutes.value)
        assertTrue(viewModel.isValid())
    }

    @Test
    fun `updateDuration sets correct values and validates`() {
        viewModel.updateHours(1)
        viewModel.updateMinutes(30)
        
        assertEquals(1, viewModel.hours.value)
        assertEquals(30, viewModel.minutes.value)
        assertTrue(viewModel.isValid())
        assertEquals(90 * 60 * 1000L, viewModel.getDurationMillis())
    }

    @Test
    fun `duration less than 15 minutes is invalid`() {
        viewModel.updateHours(0)
        viewModel.updateMinutes(10)
        
        assertFalse(viewModel.isValid())
    }

    @Test
    fun `duration more than 72 hours is invalid`() {
        viewModel.updateHours(73)
        
        // 73 is ignored, so it stays at 0. Let's force invalid by setting directly or we can't because of the check
        // The viewModel update logic limits hours to 0..72. So 73 is ignored.
        // If we want to test invalid due to 72 hours and 1 minute:
        viewModel.updateHours(72)
        viewModel.updateMinutes(1)
        
        assertFalse(viewModel.isValid())
    }
}
