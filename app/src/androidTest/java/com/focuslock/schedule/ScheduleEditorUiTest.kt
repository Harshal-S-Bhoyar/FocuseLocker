package com.focuslock.schedule

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.R
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.domain.usecase.ScheduleEngine
import com.focuslock.ui.setup.ScheduleEditorScreen
import com.focuslock.ui.setup.ScheduleEditorViewModel
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI coverage for the schedule-editor gaps: row-tap edit plus dialog error
 * hygiene. Real objects only (in-memory Room, real repository, engine, and
 * view model built directly, no Hilt), mirroring ScheduleJourneyTest.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleEditorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: FocusDatabase
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var vm: ScheduleEditorViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java)
            .allowMainThreadQueries().build()
        scheduleRepository = ScheduleRepository(database.scheduleDao(), database.appSettingDao())
        vm = ScheduleEditorViewModel(
            scheduleRepository,
            ScheduleEngine(),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun seedMondayWindow(): Long = runBlocking {
        // 08:00-10:00: start-hour +1 lands on 09:00-10:00, a Valid window.
        // (08:00-09:00 would collapse to zero-length, rejected by the engine.)
        scheduleRepository.upsertWindow(
            ScheduleWindowEntity(weekday = 1, startMinutes = 8 * 60, endMinutes = 10 * 60)
        )
    }

    private fun showScreen() {
        composeRule.setContent { ScheduleEditorScreen(onDone = {}, viewModel = vm) }
    }

    private fun saveText(): String = context.getString(R.string.schedule_save)

    private fun tapSeededRow() {
        val seeded = runBlocking { scheduleRepository.getWindowsSync() }.single()
        val rowText = vm.formatWindow(seeded)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(rowText).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(
            hasClickAction() and hasAnyAncestor(hasText(rowText)),
            useUnmergedTree = true
        ).performClick()
    }

    private fun bumpStartHourAndSave() {
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.number_picker_increase,
                context.getString(R.string.schedule_start_hour)
            )
        ).performClick()
        composeRule.onNodeWithText(saveText()).performClick()
    }

    private fun awaitEditedWindows(): List<ScheduleWindowEntity> {
        val deadline = System.currentTimeMillis() + 5_000L
        var wins = emptyList<ScheduleWindowEntity>()
        while (System.currentTimeMillis() < deadline) {
            wins = runBlocking { scheduleRepository.getWindowsSync() }
            if (wins.size == 1 && wins[0].startMinutes == 9 * 60) return wins
            Thread.sleep(50)
        }
        return wins
    }

    private fun failSaveWithFullDayOverlap(): String {
        val add = context.getString(R.string.schedule_add_window)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(add).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(add)[0].performClick()
        composeRule.onNodeWithText(context.getString(R.string.schedule_full_day)).performClick()
        composeRule.onNodeWithText(saveText()).performClick()
        val err = vm.error.value
        assertNotNull("overlap save must set an error", err)
        return err!!
    }

    @Test
    fun rowTapPrefillsDialog() {
        seedMondayWindow()
        showScreen()
        tapSeededRow()
        composeRule.onNodeWithText(saveText()).assertIsDisplayed()
        bumpStartHourAndSave()
        val wins = awaitEditedWindows()
        assertEquals(1, wins.size)
        assertEquals(9 * 60, wins[0].startMinutes)
        assertEquals(10 * 60, wins[0].endMinutes)
    }

    @Test
    fun editSaveUpdatesRow() {
        val id = seedMondayWindow()
        showScreen()
        tapSeededRow()
        bumpStartHourAndSave()
        val wins = awaitEditedWindows()
        assertEquals(1, wins.size)
        assertEquals(id, wins.single().id)
    }

    @Test
    fun overlapWarningPersists() {
        seedMondayWindow()
        showScreen()
        val err = failSaveWithFullDayOverlap()
        assertTrue(
            composeRule.onAllNodesWithText(err).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun cancelClearsError() {
        seedMondayWindow()
        showScreen()
        val err = failSaveWithFullDayOverlap()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertNull(vm.error.value)
        composeRule.onAllNodesWithText(err).assertCountEquals(0)
        composeRule.onAllNodesWithText(saveText()).assertCountEquals(0)
    }

    @Test
    fun dismissClearsError() {
        seedMondayWindow()
        showScreen()
        val err = failSaveWithFullDayOverlap()
        Espresso.pressBack()
        assertNull(vm.error.value)
        composeRule.onAllNodesWithText(err).assertCountEquals(0)
    }
}
