package com.focuslock.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.focuslock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockRequestScreen(
    viewModel: UnlockViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToOtp: () -> Unit,
    onNavigateToEmergency: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state) {
        if (state is UnlockUiState.WaitingForOtp) {
            onNavigateToOtp()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Unlock") }
            )
        }
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
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.unlock_request_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.unlock_request_description),
                style = MaterialTheme.typography.bodyLarge,
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
            
            Button(
                onClick = { 
                    viewModel.requestUnlock()
                    // Navigation will be triggered via LaunchedEffect observing state in the navigation layer
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is UnlockUiState.Requesting
            ) {
                if (state is UnlockUiState.Requesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Send Request to Trusted Contact")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel & Continue Blocking")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            TextButton(
                onClick = onNavigateToEmergency,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use Emergency Bypass (24hr Wait)")
            }
        }
    }
}
