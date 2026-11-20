package com.focuslock.schedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.domain.usecase.ScheduleEngine
import com.focuslock.ui.setup.ScheduleConfirmScreen
import com.focuslock.ui.setup.ScheduleEditorViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ScheduleConfirmArmingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun doneReEnablesAfterThrowingArm() {
        // The saved-window read throwing must fail the arming, not escape as a crash: the screen
        // stays on the confirm screen, the error is visible, and the action stays available so the
        // user can retry.
        val repository: ScheduleRepository = mockk(relaxed = true)
        coEvery { repository.getWindowsSync() } throws RuntimeException("db locked")
        every { repository.getWindows() } returns MutableStateFlow(emptyList())
        val vm = ScheduleEditorViewModel(
            repository,
            ScheduleEngine(),
            mockk(relaxed = true),
            mockk<AppSettingsRepository>(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )

        var doneCalls = 0
        composeRule.setContent {
            ScheduleConfirmScreen(onDone = { _ -> doneCalls++ }, viewModel = vm)
        }
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Done").assertIsEnabled()
        composeRule.onNodeWithText("Could not read the saved schedule. Please try again.").assertIsDisplayed()
        assertEquals("a failed arming must not navigate as if it succeeded", 0, doneCalls)
    }
}
