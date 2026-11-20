package com.focuslock.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleNavTest {

    @Test
    fun `schedule routes exist and stay unique`() {
        val routes = listOf(
            Screen.ScheduleMode.route,
            Screen.ScheduleEditor.route,
            Screen.ScheduleConfirm.route,
            Screen.ScheduleManage.route,
            Screen.KeywordSelection.route,
            Screen.TimerSetup.route
        )
        assertTrue(routes.all { it.isNotBlank() })
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `schedule confirm with a live window hands off to the session confirmation screen`() {
        // live=true marks the schedule hand-off, so the confirmation screen may enter the window
        // that is already running instead of activating a second session.
        assertEquals(
            Screen.SessionConfirmation.route + "/3600000?live=true",
            scheduleConfirmNav(3_600_000L)
        )
    }

    @Test
    fun `one-shot hand-off carries no live flag`() {
        // Without the flag the shortcut cannot fire, so a running session is never adopted.
        assertEquals(
            Screen.SessionConfirmation.route + "/3600000",
            verifyNav(3_600_000L).dest
        )
    }

    @Test
    fun `schedule confirm with future window stays in terminal armed state without redirect`() {
        // Must return null so AppNavigation does not redirect to the CategorySelection wizard.
        assertEquals(null, scheduleConfirmNav(null))
    }
}
