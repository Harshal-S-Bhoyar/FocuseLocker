package com.focuslock.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.focuslock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpEntryScreen(
    viewModel: UnlockViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToCooldown: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var otpValue by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is UnlockUiState.CooldownActive) {
            onNavigateToCooldown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter Unlock Code", fontWeight = FontWeight.SemiBold) },
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
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = stringResource(R.string.otp_entry_sent_message),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.otp_entry_instruction),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = otpValue,
                onValueChange = { if (it.length <= 6) otpValue = it },
                label = { Text("6-Digit Code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (state is UnlockUiState.Error) {
                Text(
                    text = (state as UnlockUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (state is UnlockUiState.VerifyingOtp) {
                com.focuslock.ui.common.CyberpunkLoadingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(vertical = 16.dp)
                )
            } else {
                com.focuslock.ui.components.PrimaryButton(
                    label = "VERIFY CODE",
                    onClick = { viewModel.verifyOtp(otpValue) },
                    enabled = otpValue.length == 6
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            com.focuslock.ui.components.PrimaryButton(
                label = "CANCEL",
                onClick = onNavigateBack
            )
        }
    }
}
