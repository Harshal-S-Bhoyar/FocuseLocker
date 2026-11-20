package com.focuslock.ui.navigation

import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import com.focuslock.domain.usecase.AddKeywordUseCase
import com.focuslock.domain.usecase.RemoveKeywordUseCase
import com.focuslock.domain.usecase.HasVerifiedContactUseCase
import com.focuslock.ui.setup.KeywordSelectionViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class FirstTimeFlowTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `verified keywords go straight to chooser`() {
        assertEquals(Screen.ScheduleMode.route, chooserOrContactRoute(true))
    }

    @Test
    fun `unverified keywords go to pre-chooser contact`() {
        assertEquals(Screen.TrustedContactSetup.route + "/-1", chooserOrContactRoute(false))
    }

    @Test
    fun `pre-chooser contact returns to chooser`() {
        val nav = verifyNav(-1L)
        assertEquals(Screen.ScheduleMode.route, nav.dest)
        assertEquals(Screen.KeywordSelection.route, nav.popUpTo)
    }

    @Test
    fun `guard contact returns to schedule confirm`() {
        val nav = verifyNav(0L)
        assertEquals(Screen.ScheduleConfirm.route, nav.dest)
        assertEquals(Screen.CategorySelection.route, nav.popUpTo)
    }

    @Test
    fun `one-shot contact returns to session confirmation with duration`() {
        val nav = verifyNav(3_600_000L)
        assertEquals(Screen.SessionConfirmation.route + "/3600000", nav.dest)
        assertEquals(Screen.CategorySelection.route, nav.popUpTo)
    }

    @Test
    fun `positive durations always preserve the duration in dest`() {
        listOf(60_000L, 3_600_000L, 604_800_000L, Long.MAX_VALUE).forEach { d ->
            val nav = verifyNav(d)
            assertTrue("dest must end with /$d: ${nav.dest}", nav.dest.endsWith("/$d"))
            assertFalse("dest must never be blank", nav.dest.isBlank())
            assertFalse("popUpTo must never be blank", nav.popUpTo.isBlank())
        }
    }

    @Test
    fun `keywords viewmodel exposes unverified flag for routing`() = runTest {
        val hasVerifiedContactUseCase = mockk<HasVerifiedContactUseCase>()
        coEvery { hasVerifiedContactUseCase() } returns false
        val viewModel = KeywordSelectionViewModel(
            mockk<GetAllKeywordsUseCase>().also { every { it() } returns flowOf(emptyList()) },
            mockk<AddKeywordUseCase>(),
            mockk<RemoveKeywordUseCase>(relaxed = true),
            hasVerifiedContactUseCase
        )
        assertFalse(viewModel.hasVerifiedContact.value)
    }

    @Test
    fun `keywords viewmodel exposes verified flag for routing`() = runTest {
        val hasVerifiedContactUseCase = mockk<HasVerifiedContactUseCase>()
        coEvery { hasVerifiedContactUseCase() } returns true
        val viewModel = KeywordSelectionViewModel(
            mockk<GetAllKeywordsUseCase>().also { every { it() } returns flowOf(emptyList()) },
            mockk<AddKeywordUseCase>(),
            mockk<RemoveKeywordUseCase>(relaxed = true),
            hasVerifiedContactUseCase
        )
        assertTrue(viewModel.hasVerifiedContact.value)
    }
}
