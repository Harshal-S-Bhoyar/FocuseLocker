package com.focuslock.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.focuslock.R
import com.focuslock.ui.components.PrimaryButton
import com.focuslock.ui.components.PrimaryButton
import com.focuslock.ui.components.PrimaryButton
import com.focuslock.ui.common.CyberpunkLoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyBypassScreen(
    viewModel: UnlockViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    
    // State automatically updates via ViewModel Flow collection
    LaunchedEffect(state) {
        if (state is UnlockUiState.Unlocked) {
            onNavigateToHome()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Bypass", fontWeight = FontWeight.SemiBold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = if (state is UnlockUiState.EmergencyBypassActive) "Emergency Bypass Active" else "Use Emergency Bypass?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.emergency_bypass_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (state is UnlockUiState.Error) {
                Text(
                    text = (state as UnlockUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            if (state is UnlockUiState.EmergencyBypassActive) {
                val endsAt = (state as UnlockUiState.EmergencyBypassActive).endsAt
                if (endsAt > 0) {
                    val expiryTimeStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(endsAt))
                    
                    Text(
                        text = stringResource(R.string.emergency_bypass_unlocks_at, expiryTimeStr),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                PrimaryButton(
                    label = "Return to Home",
                    onClick = onNavigateToHome,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                if (state is UnlockUiState.Requesting) {
                    CyberpunkLoadingIndicator(
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                } else {
                    PrimaryButton(
                        label = "Start 24-Hour Wait",
                        onClick = { viewModel.startEmergencyBypass() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PrimaryButton(
                    label = "Cancel",
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
