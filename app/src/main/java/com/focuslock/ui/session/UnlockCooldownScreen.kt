package com.focuslock.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.focuslock.R
import com.focuslock.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockCooldownScreen(
    viewModel: UnlockViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit,
    onCooldownExpired: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    
    // Get the endsAt timestamp from the ViewModel state
    val endsAt = (state as? UnlockUiState.CooldownActive)?.endsAt ?: 0L

    // Live countdown in milliseconds — ticks every second
    var remainingMillis by remember(endsAt) {
        mutableStateOf(maxOf(0L, endsAt - System.currentTimeMillis()))
    }

    // Tick the countdown every second
    LaunchedEffect(endsAt) {
        while (remainingMillis > 0L) {
            delay(1000L)
            remainingMillis = maxOf(0L, endsAt - System.currentTimeMillis())
        }
        // Countdown reached zero — cooldown is over
        if (endsAt > 0L && remainingMillis <= 0L) {
            viewModel.completeCooldownAndKillSession()
            onCooldownExpired()
        }
    }

    // Format remainingMillis as MM:SS
    val minutes = (remainingMillis / 1000) / 60
    val seconds = (remainingMillis / 1000) % 60
    val countdownText = String.format("%02d:%02d", minutes, seconds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cooldown Active", fontWeight = FontWeight.SemiBold) },
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
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.unlock_cooldown_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.unlock_cooldown_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // LIVE countdown display — replaces static clock string
            if (endsAt > 0L) {
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "remaining before unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                label = "Return to Active Session",
                onClick = onNavigateToHome,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
