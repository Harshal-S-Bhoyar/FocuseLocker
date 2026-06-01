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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.focuslock.R
import java.util.Locale

/**
 * Screen displaying the active focus session.
 * Shows the live countdown and provides the option to request an early unlock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    onNavigateToSessionComplete: () -> Unit,
    onNavigateToUnlockRequest: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel()
) {
    val remainingTimeMillis by viewModel.remainingTimeMillis.collectAsStateWithLifecycle()
    val protectionStatus by viewModel.protectionStatus.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshProtectionStatus()
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
                title = { Text(stringResource(R.string.active_session_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensionResource(R.dimen.spacing_xl)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Text(
                text = stringResource(R.string.active_session_time_remaining),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_lg)))
            
            // Countdown Timer Display
            if (remainingTimeMillis == -1L) {
                // Initial loading state
                CircularProgressIndicator()
            } else {
                Text(
                    text = formatTime(remainingTimeMillis),
                    fontSize = MaterialTheme.typography.displayLarge.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_xxxl)))
            
            // Status Indicators
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(dimensionResource(R.dimen.spacing_lg)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_md))
                ) {
                    StatusRow(label = stringResource(R.string.active_session_vpn_permission), isActive = protectionStatus.vpnPermissionGranted)
                    StatusRow(label = stringResource(R.string.active_session_accessibility), isActive = protectionStatus.accessibilityEnabled)
                    StatusRow(label = stringResource(R.string.active_session_uninstall_protection), isActive = protectionStatus.deviceAdminEnabled)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Early Unlock Button (Navigates to Phase 6 flow)
            Button(
                onClick = onNavigateToUnlockRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.button_height)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(stringResource(R.string.active_session_request_unlock), style = MaterialTheme.typography.titleMedium)
            }
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
fun StatusRow(label: String, isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = stringResource(if (isActive) R.string.status_active else R.string.status_inactive),
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.spacing_md)))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
