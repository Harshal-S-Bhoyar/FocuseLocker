package com.focuslock.ui.setup

import com.focuslock.domain.usecase.HasVerifiedContactUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerSetupViewModelTest {

    private lateinit var useCase: HasVerifiedContactUseCase
    private lateinit var viewModel: TimerSetupViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        coEvery { useCase() } returns false
        viewModel = TimerSetupViewModel(useCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test valid duration limits`() = runTest {
        // Test normal valid setup
        viewModel.updateHours(2)
        viewModel.updateMinutes(30)
        assertTrue(viewModel.isValid())
        assertEquals((2 * 60 + 30) * 60 * 1000L, viewModel.getDurationMillis())

        // Test boundary 168 hours
        viewModel.updateHours(168)
        viewModel.updateMinutes(0)
        assertTrue(viewModel.isValid())
        assertEquals((168 * 60) * 60 * 1000L, viewModel.getDurationMillis())

        // Test beyond 168 hours (invalid theoretically but update doesn't allow beyond 168 directly)
        viewModel.updateHours(169)
        // should stay at 168
        assertEquals(168, viewModel.hours.value)

        // However, if one updates minutes to 1 at 168 hours, it should cap via getDurationMillis()
        viewModel.updateMinutes(1)
        // Note: isValid() returns false because totalMinutes > 168 * 60
        assertFalse(viewModel.isValid())
        
        // getDurationMillis caps at 168 hours
        assertEquals((168 * 60) * 60 * 1000L, viewModel.getDurationMillis())
    }

    @Test
    fun `test minimum duration limit`() = runTest {
        viewModel.updateHours(0)
        viewModel.updateMinutes(0)
        // isValid should be false (1 min minimum)
        assertFalse(viewModel.isValid())
        
        // getDurationMillis should coerce to 1 minute
        assertEquals(60 * 1000L, viewModel.getDurationMillis())
    }
}
