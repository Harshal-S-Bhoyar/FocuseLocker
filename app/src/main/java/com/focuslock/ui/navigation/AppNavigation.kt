package com.focuslock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.compose.runtime.remember
import com.focuslock.ui.onboarding.OnboardingScreen
import com.focuslock.ui.onboarding.PermissionSetupScreen
import com.focuslock.ui.onboarding.TrustedContactSetupScreen
import com.focuslock.ui.onboarding.VerifyContactScreen

// ── Route definitions ─────────────────────────────────────────────────────────

sealed class Screen(val route: String) {

    // Onboarding
    object Onboarding : Screen("onboarding")
    object PermissionSetup : Screen("permission_setup")
    object TrustedContactSetup : Screen("trusted_contact_setup")
    object VerifyContact : Screen("verify_contact")

    // Setup (Phase 4)
    object CategorySelection : Screen("category_selection")
    object AppBlockSelection : Screen("app_block_selection")
    object KeywordSelection : Screen("keyword_selection")
    object ScheduleMode : Screen("schedule_mode")
    object ScheduleEditor : Screen("schedule_editor")
    object ScheduleConfirm : Screen("schedule_confirm")
    object ScheduleManage : Screen("schedule_manage")
    object TimerSetup : Screen("timer_setup")
    object SessionConfirmation : Screen("session_confirmation")

    // Active session (Phase 4)
    object ActiveSession : Screen("active_session")

    // Unlock flow (Phase 6)
    object UnlockFlow : Screen("unlock_flow")
    object UnlockRequest : Screen("unlock_request")
    object OtpEntry : Screen("otp_entry")
    object UnlockCooldown : Screen("unlock_cooldown")
    object EmergencyBypass : Screen("emergency_bypass")

    // End (Phase 6)
    object SessionComplete : Screen("session_complete")
}

// ponytail: pure routing decisions for the first-time flow, unit-tested in FirstTimeFlowTest.
// -1L denotes pre-chooser contact (0L is the editor-guard path, positive is a one-shot duration).
internal fun chooserOrContactRoute(hasVerifiedContact: Boolean): String =
    if (hasVerifiedContact) Screen.ScheduleMode.route
    else Screen.TrustedContactSetup.route + "/-1"

internal data class VerifyNav(val dest: String, val popUpTo: String)

internal fun verifyNav(durationMillis: Long): VerifyNav = when {
    durationMillis < 0L -> VerifyNav(Screen.ScheduleMode.route, Screen.KeywordSelection.route)
    durationMillis == 0L -> VerifyNav(Screen.ScheduleConfirm.route, Screen.CategorySelection.route)
    else -> VerifyNav(
        Screen.SessionConfirmation.route + "/$durationMillis",
        Screen.CategorySelection.route
    )
}

// ponytail: pure routing decisions for schedule confirmation and category selection
// ponytail: live=true marks the schedule hand-off for a window that is already running, so the
// confirmation screen may enter it instead of activating a second session. Other callers omit it
// and the route's default (false) applies.
internal fun scheduleConfirmNav(liveRemainingMillis: Long?): String? =
    liveRemainingMillis?.let { Screen.SessionConfirmation.route + "/$it?live=true" }

// ── Navigation host ───────────────────────────────────────────────────────────

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Onboarding.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // ── Phase 2 — Onboarding (LIVE) ───────────────────────────────────────

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onNavigateToPermissions = {
                    navController.navigate(Screen.PermissionSetup.route)
                },
                onNavigateToActiveSession = {
                    navController.navigate(Screen.ActiveSession.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onNavigateToCategorySelection = {
                    navController.navigate(Screen.CategorySelection.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PermissionSetup.route) {
            PermissionSetupScreen(
                onNavigateNext = {
                    navController.navigate(Screen.CategorySelection.route)
                }
            )
        }

        composable(
            route = Screen.TrustedContactSetup.route + "/{durationMillis}",
            arguments = listOf(androidx.navigation.navArgument("durationMillis") { type = androidx.navigation.NavType.LongType })
        ) { backStackEntry ->
            val durationMillis = backStackEntry.arguments?.getLong("durationMillis") ?: 0L
            TrustedContactSetupScreen(
                onNavigateToVerify = {
                    navController.navigate(Screen.VerifyContact.route + "/$durationMillis")
                }
            )
        }

        composable(
            route = Screen.VerifyContact.route + "/{durationMillis}",
            arguments = listOf(androidx.navigation.navArgument("durationMillis") { type = androidx.navigation.NavType.LongType })
        ) { backStackEntry ->
            val durationMillis = backStackEntry.arguments?.getLong("durationMillis") ?: 0L
            VerifyContactScreen(
                onNavigateToSetup = {
                    // Route by entry path (chooserOrContactRoute documents the sentinels).
                    val nav = verifyNav(durationMillis)
                    navController.navigate(nav.dest) {
                        popUpTo(nav.popUpTo) { inclusive = true }
                    }
                }
            )
        }

        // ── Phase 4 — Setup & Session (LIVE) ───────────────────────────

        composable(Screen.CategorySelection.route) {
            com.focuslock.ui.setup.CategorySelectionScreen(
                onNavigateNext = {
                    navController.navigate(Screen.AppBlockSelection.route)
                },
                onNavigateSchedule = {
                    // Continue into the weekly editor, whose Done confirms forward. Never the
                    // manage route: popping back from there is the loop this fixes.
                    navController.navigate(Screen.ScheduleEditor.route)
                }
            )
        }

        composable(Screen.AppBlockSelection.route) {
            com.focuslock.ui.setup.AppBlockScreen(
                onNavigateNext = {
                    navController.navigate(Screen.KeywordSelection.route)
                }
            )
        }

        composable(Screen.KeywordSelection.route) {
            com.focuslock.ui.setup.KeywordSelectionScreen(
                onNavigateNext = { hasVerifiedContact ->
                    // First-time setup: verify contact before choosing session type.
                    navController.navigate(chooserOrContactRoute(hasVerifiedContact))
                }
            )
        }

        composable(Screen.ScheduleMode.route) {
            com.focuslock.ui.setup.ScheduleModeScreen(
                onSchedule = {
                    navController.navigate(Screen.ScheduleEditor.route)
                },
                onOneShot = {
                    navController.navigate(Screen.TimerSetup.route)
                }
            )
        }

        composable(Screen.ScheduleEditor.route) {
            com.focuslock.ui.setup.ScheduleEditorScreen(
                onDone = { hasVerifiedContact ->
                    if (hasVerifiedContact) {
                        navController.navigate(Screen.ScheduleConfirm.route) {
                            popUpTo(Screen.ScheduleMode.route) { inclusive = true }
                        }
                    } else {
                        // User needs trusted contact setup first; pass 0L to denote schedule path
                        navController.navigate(Screen.TrustedContactSetup.route + "/0") {
                            popUpTo(Screen.ScheduleMode.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.ScheduleConfirm.route) {
            com.focuslock.ui.setup.ScheduleConfirmScreen(
                onDone = { liveRemainingMillis ->
                    // ponytail: a live window hands off to the session confirmation screen; a future
                    // window leaves dest null and stays on ScheduleConfirmScreen in its armed terminal state.
                    scheduleConfirmNav(liveRemainingMillis)?.let { dest ->
                        navController.navigate(dest)
                    }
                }
            )
        }

        composable(Screen.ScheduleManage.route) {
            com.focuslock.ui.setup.ScheduleEditorScreen(
                onDone = { _ -> navController.popBackStack() },
                showSwitch = true
            )
        }

        composable(Screen.TimerSetup.route) {
            com.focuslock.ui.setup.TimerSetupScreen(
                onNavigateNext = { durationMillis, hasVerifiedContact ->
                    if (hasVerifiedContact) {
                        navController.navigate(Screen.SessionConfirmation.route + "/$durationMillis")
                    } else {
                        navController.navigate(Screen.TrustedContactSetup.route + "/$durationMillis")
                    }
                }
            )
        }

        composable(
            route = Screen.SessionConfirmation.route + "/{durationMillis}?live={live}",
            arguments = listOf(
                androidx.navigation.navArgument("durationMillis") { type = androidx.navigation.NavType.LongType },
                // Optional: routes that omit the query string still resolve and read false.
                androidx.navigation.navArgument("live") {
                    type = androidx.navigation.NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            com.focuslock.ui.setup.SessionConfirmationScreen(
                onNavigateToActiveSession = {
                    navController.navigate(Screen.ActiveSession.route) {
                        // Clear the setup backstack once the session is active
                        popUpTo(Screen.CategorySelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ActiveSession.route) {
            com.focuslock.ui.session.ActiveSessionScreen(
                onNavigateToSessionComplete = {
                    navController.navigate(Screen.SessionComplete.route) {
                        popUpTo(Screen.ActiveSession.route) { inclusive = true }
                    }
                },
                onNavigateToUnlockRequest = {
                    navController.navigate(Screen.UnlockFlow.route)
                }
            )
        }

        // ── Phase 6 — Unlock flow (Placeholder) ──────────────────────────────

        navigation(
            startDestination = Screen.UnlockRequest.route,
            route = Screen.UnlockFlow.route
        ) {
            composable(Screen.UnlockRequest.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.UnlockFlow.route)
                }
                com.focuslock.ui.session.UnlockRequestScreen(
                    viewModel = androidx.hilt.navigation.compose.hiltViewModel(parentEntry),
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToOtp = { 
                        navController.navigate(Screen.OtpEntry.route) {
                            popUpTo(Screen.UnlockRequest.route) { inclusive = true }
                        }
                    },
                    onNavigateToCooldown = {
                        navController.navigate(Screen.UnlockCooldown.route) {
                            popUpTo(Screen.UnlockRequest.route) { inclusive = true }
                        }
                    },
                    onNavigateToEmergency = {
                        navController.navigate(Screen.EmergencyBypass.route)
                    }
                )
            }

            composable(Screen.OtpEntry.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.UnlockFlow.route)
                }
                com.focuslock.ui.session.OtpEntryScreen(
                    viewModel = androidx.hilt.navigation.compose.hiltViewModel(parentEntry),
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCooldown = {
                        navController.navigate(Screen.UnlockCooldown.route) {
                            popUpTo(Screen.ActiveSession.route) { inclusive = false }
                        }
                    }
                )
            }

            composable(Screen.UnlockCooldown.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.UnlockFlow.route)
                }
                com.focuslock.ui.session.UnlockCooldownScreen(
                    viewModel = androidx.hilt.navigation.compose.hiltViewModel(parentEntry),
                    onNavigateToHome = {
                        navController.navigate(Screen.ActiveSession.route) {
                            popUpTo(Screen.UnlockCooldown.route) { inclusive = true }
                        }
                    },
                    onCooldownExpired = {
                        navController.navigate(Screen.SessionComplete.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.EmergencyBypass.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.UnlockFlow.route)
                }
                com.focuslock.ui.session.EmergencyBypassScreen(
                    viewModel = androidx.hilt.navigation.compose.hiltViewModel(parentEntry),
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToHome = {
                        navController.navigate(Screen.SessionComplete.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Screen.SessionComplete.route) {
            com.focuslock.ui.session.SessionCompleteScreen(
                onStartAnotherSession = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.SessionComplete.route) { inclusive = true }
                    }
                },
                onNavigateToSchedule = {
                    navController.navigate(Screen.ScheduleManage.route)
                }
            )
        }
    }
}
