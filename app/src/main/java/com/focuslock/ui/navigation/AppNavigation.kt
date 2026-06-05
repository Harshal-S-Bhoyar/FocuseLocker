package com.focuslock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    object KeywordSelection : Screen("keyword_selection")
    object TimerSetup : Screen("timer_setup")
    object SessionConfirmation : Screen("session_confirmation")

    // Active session (Phase 4)
    object ActiveSession : Screen("active_session")

    // Unlock flow (Phase 6)
    object UnlockRequest : Screen("unlock_request")
    object OtpEntry : Screen("otp_entry")
    object UnlockCooldown : Screen("unlock_cooldown")
    object EmergencyBypass : Screen("emergency_bypass")

    // End (Phase 6)
    object SessionComplete : Screen("session_complete")
}

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
                    // Onboarding complete — go to session confirmation
                    navController.navigate(Screen.SessionConfirmation.route + "/$durationMillis") {
                        popUpTo(Screen.CategorySelection.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Phase 4 — Setup & Session (LIVE) ───────────────────────────

        composable(Screen.CategorySelection.route) {
            com.focuslock.ui.setup.CategorySelectionScreen(
                onNavigateNext = {
                    navController.navigate(Screen.KeywordSelection.route)
                }
            )
        }

        composable(Screen.KeywordSelection.route) {
            com.focuslock.ui.setup.KeywordSelectionScreen(
                onNavigateNext = {
                    navController.navigate(Screen.TimerSetup.route)
                }
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
            route = Screen.SessionConfirmation.route + "/{durationMillis}",
            arguments = listOf(androidx.navigation.navArgument("durationMillis") { type = androidx.navigation.NavType.LongType })
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
                    navController.navigate(Screen.UnlockRequest.route)
                }
            )
        }

        // ── Phase 6 — Unlock flow (Placeholder) ──────────────────────────────

        composable(Screen.UnlockRequest.route) {
            com.focuslock.ui.session.UnlockRequestScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToOtp = { 
                    navController.navigate(Screen.OtpEntry.route) {
                        popUpTo(Screen.UnlockRequest.route) { inclusive = true }
                    }
                },
                onNavigateToEmergency = {
                    navController.navigate(Screen.EmergencyBypass.route)
                }
            )
        }

        composable(Screen.OtpEntry.route) {
            com.focuslock.ui.session.OtpEntryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCooldown = {
                    navController.navigate(Screen.UnlockCooldown.route) {
                        popUpTo(Screen.ActiveSession.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.UnlockCooldown.route) {
            com.focuslock.ui.session.UnlockCooldownScreen(
                onNavigateToHome = {
                    // Navigate to ActiveSession — session is still running during cooldown.
                    // Use popUpTo(UnlockCooldown) inclusive=true so back button
                    // doesn't return to this screen.
                    navController.navigate(Screen.ActiveSession.route) {
                        popUpTo(Screen.UnlockCooldown.route) { inclusive = true }
                    }
                },
                onCooldownExpired = {
                    // Cooldown timer reached zero — session is deactivated by CooldownTimerWorker.
                    // Navigate to SessionComplete and clear entire stack.
                    navController.navigate(Screen.SessionComplete.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.EmergencyBypass.route) {
            com.focuslock.ui.session.EmergencyBypassScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SessionComplete.route) {
            com.focuslock.ui.session.SessionCompleteScreen(
                onStartAnotherSession = {
                    navController.navigate(Screen.CategorySelection.route) {
                        popUpTo(Screen.SessionComplete.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
