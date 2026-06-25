package com.focuslock.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import com.focuslock.ui.common.CyberpunkLoadingIndicator
import com.focuslock.ui.common.FullScreenBannerLoader
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.R

/**
 * Stateless composable — receives state and callbacks only.
 * No logic here per architecture rules.
 */
@Composable
fun OnboardingScreen(
    onNavigateToPermissions: () -> Unit,
    onNavigateToActiveSession: () -> Unit,
    onNavigateToCategorySelection: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    // Handle one-time navigation events
    LaunchedEffect(state.event) {
        when (state.event) {
            is OnboardingEvent.NavigateToPermissions -> {
                viewModel.onEventConsumed()
                onNavigateToPermissions()
            }
            is OnboardingEvent.NavigateToActiveSession -> {
                viewModel.onEventConsumed()
                onNavigateToActiveSession()
            }
            is OnboardingEvent.NavigateToCategorySelection -> {
                viewModel.onEventConsumed()
                onNavigateToCategorySelection()
            }
            is OnboardingEvent.NavigateToPermissionRepair -> {
                viewModel.onEventConsumed()
                onNavigateToPermissions()
            }
            is OnboardingEvent.StartPermissionEnforcement -> {
                viewModel.onEventConsumed()
                com.focuslock.service.overlay.PermissionEnforcementService.start(context)
                onNavigateToActiveSession()
            }
            null -> Unit
        }
    }

    OnboardingContent(
        isLoading = state.isLoading,
        onGetStartedClicked = viewModel::onGetStartedClicked
    )
}

@Composable
private fun OnboardingContent(
    isLoading: Boolean,
    onGetStartedClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            FullScreenBannerLoader(modifier = Modifier.fillMaxSize())
            return
        }

        Box(contentAlignment = Alignment.Center) {
            // Shadow / Glow effect behind the icon
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .blur(16.dp),
                tint = com.focuslock.ui.theme.GlowCyan
            )
            // 3D Textured foreground icon
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        val brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White,
                                com.focuslock.ui.theme.CyanPrimary,
                                com.focuslock.ui.theme.VioletSecondary
                            )
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush, blendMode = BlendMode.SrcAtop)
                        }
                    },
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        com.focuslock.ui.components.PrimaryButton(
            onClick = onGetStartedClicked,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            label = stringResource(R.string.onboarding_get_started)
        )
    }
}
