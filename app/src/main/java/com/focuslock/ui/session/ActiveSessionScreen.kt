package com.focuslock.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
// import removed
import androidx.compose.material3.Card
import com.focuslock.ui.common.CyberpunkLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.focuslock.R
import java.util.Locale

/**
 * Screen displaying the active focus session.
 * Shows the live countdown and provides the option to request an early unlock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@android.annotation.SuppressLint("BatteryLife")
@Composable
fun ActiveSessionScreen(
    onNavigateToSessionComplete: () -> Unit,
    onNavigateToUnlockRequest: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel()
) {
    val remainingTimeMillis by viewModel.remainingTimeMillis.collectAsStateWithLifecycle()
    val protectionStatus by viewModel.protectionStatus.collectAsStateWithLifecycle()
    val unlockInfo by viewModel.activeUnlockInfo.collectAsStateWithLifecycle()
    val sessionActive by viewModel.sessionActive.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshProtectionStatus()
    }

    // Automatically navigate when the session disappears (e.g. killed by background worker after cooldown)
    LaunchedEffect(sessionActive) {
        if (sessionActive == false) {
            onNavigateToSessionComplete()
        }
    }

    // Automatically navigate when the timer reaches zero
    LaunchedEffect(remainingTimeMillis) {
        if (remainingTimeMillis == 0L) {
            onNavigateToSessionComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.active_session_title), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Text(
                text = stringResource(R.string.active_session_time_remaining),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Countdown Timer Display
            if (remainingTimeMillis == -1L) {
                // Initial loading state
                CyberpunkLoadingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                )
            } else {
                Text(
                    text = formatTime(remainingTimeMillis),
                    fontSize = MaterialTheme.typography.displayLarge.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Status Indicators
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatusRow(label = stringResource(R.string.active_session_vpn_permission), isActive = protectionStatus.vpnPermissionGranted)
                    StatusRow(label = stringResource(R.string.active_session_accessibility), isActive = protectionStatus.accessibilityEnabled)
                    StatusRow(label = stringResource(R.string.active_session_uninstall_protection), isActive = protectionStatus.deviceAdminEnabled)
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    StatusRow(
                        label = "Battery Unrestricted", 
                        isActive = protectionStatus.batteryOptimizationIgnored,
                        onClick = {
                            if (!protectionStatus.batteryOptimizationIgnored) {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback to regular battery optimization settings if request fails
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e2: Exception) {
                                        timber.log.Timber.e(e2, "Failed to open battery optimization settings")
                                        // Ignore
                                    }
                                }
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Cooldown Timer Card (Shows if Cooldown is active)
            if (unlockInfo?.status == com.focuslock.domain.model.UnlockRequestStatus.COOLDOWN) {
                val endsAt = unlockInfo?.cooldownEndsAt ?: 0L
                var cooldownRemainingMillis by remember(endsAt) {
                    mutableLongStateOf(maxOf(0L, endsAt - System.currentTimeMillis()))
                }

                LaunchedEffect(endsAt) {
                    while (cooldownRemainingMillis > 0L) {
                        delay(1000L)
                        cooldownRemainingMillis = maxOf(0L, endsAt - System.currentTimeMillis())
                    }
                    if (endsAt > 0L && cooldownRemainingMillis <= 0L) {
                        viewModel.completeCooldownAndKillSession()
                    }
                }

                if (endsAt > 0L) {
                    val minutes = (cooldownRemainingMillis / 1000) / 60
                    val seconds = (cooldownRemainingMillis / 1000) % 60
                    val countdownText = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = androidx.compose.material3.CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = com.focuslock.ui.theme.CustomIcons.HourglassEmpty,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Cooldown Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = countdownText,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            
            // Early Unlock Button
            com.focuslock.ui.components.PrimaryButton(
                label = stringResource(R.string.active_session_request_unlock).uppercase(),
                onClick = onNavigateToUnlockRequest
            )
        }
    }
}

/**
 * Helper to format milliseconds into HH:MM:SS
 */
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun StatusRow(label: String, isActive: Boolean, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = stringResource(if (isActive) R.string.status_active else R.string.status_inactive),
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label, 
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
