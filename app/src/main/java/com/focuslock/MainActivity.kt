package com.focuslock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.focuslock.ui.navigation.AppNavigation
import com.focuslock.ui.navigation.Screen
import com.focuslock.ui.theme.FocusLockTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.focuslock.data.repository.SessionRepository

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    private val _navigateTo = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        _navigateTo.value = resolveNavigationTarget(intent)

        setContent {
            FocusLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navigateTo by remember { _navigateTo }

                    val startDest = when (navigateTo) {
                        "active_session" -> Screen.ActiveSession.route
                        "early_unlock" -> Screen.UnlockFlow.route
                        else -> Screen.Onboarding.route
                    }

                    // Start destination will be determined by onboarding state in Phase 2.
                    // For now always start at Onboarding.
                    AppNavigation(
                        navController = navController,
                        startDestination = startDest
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        _navigateTo.value = resolveNavigationTarget(intent)
    }

    private fun resolveNavigationTarget(intent: Intent): String? {
        val target = intent.getStringExtra("navigate_to") ?: return null
        // Only honor navigation if it came from our own app's package
        val callingPkg = callingActivity?.packageName ?: referrer?.host
        val isTrusted = callingPkg == null || callingPkg == packageName
        if (!isTrusted) return null
        return target
    }
}
