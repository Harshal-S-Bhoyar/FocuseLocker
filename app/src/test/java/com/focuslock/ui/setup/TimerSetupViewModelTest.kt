package com.focuslock.ui.setup

import com.focuslock.domain.usecase.HasVerifiedContactUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

        // Test boundary 7 days exact
        viewModel.updateHours(0)
        viewModel.updateMinutes(0)
        viewModel.updateDays(7)
        assertEquals(7, viewModel.days.value)
        assertTrue(viewModel.isValid())
        assertEquals((7 * 24 * 60) * 60 * 1000L, viewModel.getDurationMillis())

        // 7 days with nonzero minutes is invalid (minutes set directly, continue gated)
        viewModel.updateMinutes(1)
        assertFalse(viewModel.isValid())

        // getDurationMillis stays exact below the cap (gated updates can't exceed it)
        viewModel.updateMinutes(0)
        viewModel.updateDays(6)
        viewModel.updateHours(23)
        viewModel.updateMinutes(59)
        assertTrue(viewModel.isValid())
        assertEquals((6 * 24 * 60 + 23 * 60 + 59) * 60 * 1000L, viewModel.getDurationMillis())
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

    @Test
    fun `initialization reflects a verified contact`() = runTest {
        coEvery { useCase() } returns true
        val contactViewModel = TimerSetupViewModel(useCase)

        advanceUntilIdle()

        assertTrue(contactViewModel.hasVerifiedContact.value)
    }

    @Test
    fun `out of range hour updates are ignored`() = runTest {
        viewModel.updateHours(4)
        viewModel.updateHours(-1)
        viewModel.updateHours(169)

        assertEquals(4, viewModel.hours.value)
    }

    @Test
    fun `out of range minute updates are ignored`() = runTest {
        viewModel.updateMinutes(30)
        viewModel.updateMinutes(-1)
        viewModel.updateMinutes(60)

        assertEquals(30, viewModel.minutes.value)
    }

    @Test
    fun `hour increment rolls over into days`() = runTest {
        viewModel.updateHours(23)
        viewModel.updateMinutes(0)
        viewModel.updateHours(24)

        assertEquals(1, viewModel.days.value)
        assertEquals(0, viewModel.hours.value)
        assertEquals((24 * 60) * 60 * 1000L, viewModel.getDurationMillis())
    }

    @Test
    fun `hour decrement borrows a day`() = runTest {
        viewModel.updateDays(1)
        viewModel.updateMinutes(0)
        viewModel.updateHours(0)
        viewModel.updateHours(-1)

        assertEquals(0, viewModel.days.value)
        assertEquals(23, viewModel.hours.value)
    }

    @Test
    fun `rollover past 7 days is refused`() = runTest {
        viewModel.updateDays(6)
        viewModel.updateHours(23)
        viewModel.updateMinutes(59)
        viewModel.updateHours(24)

        assertEquals(6, viewModel.days.value)
        assertEquals(23, viewModel.hours.value)
        assertTrue(viewModel.isValid())
    }

    @Test
    fun `days reach 7 only with hours and minutes at zero`() = runTest {
        viewModel.updateHours(5)
        viewModel.updateDays(7)
        assertEquals(0, viewModel.days.value)

        viewModel.updateHours(0)
        viewModel.updateMinutes(0)
        viewModel.updateDays(7)
        assertEquals(7, viewModel.days.value)
        assertTrue(viewModel.isValid())
    }

    @Test
    fun `out of range day updates are ignored`() = runTest {
        viewModel.updateDays(3)
        viewModel.updateDays(-1)
        viewModel.updateDays(8)

        assertEquals(3, viewModel.days.value)
    }
}
