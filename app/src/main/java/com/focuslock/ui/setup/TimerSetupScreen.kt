package com.focuslock.ui.setup

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.R

/**
 * Screen where users set the duration for their focus session.
 * It enforces boundaries (min 15 mins, max 72 hours) visually and logically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSetupScreen(
    onNavigateNext: (Long) -> Unit,
    viewModel: TimerSetupViewModel = hiltViewModel()
) {
    val hours by viewModel.hours.collectAsStateWithLifecycle()
    val minutes by viewModel.minutes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timer_setup_title)) }
            )
        },
        bottomBar = {
            Button(
                onClick = { onNavigateNext(viewModel.getDurationMillis()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.spacing_lg)),
                enabled = viewModel.isValid()
            ) {
                Text(stringResource(R.string.timer_review))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensionResource(R.dimen.spacing_lg)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.timer_setup_prompt),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_xxl)))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Hours Picker
                NumberPicker(
                    value = hours,
                    label = stringResource(R.string.timer_hours),
                    onIncrement = { viewModel.updateHours(hours + 1) },
                    onDecrement = { viewModel.updateHours(hours - 1) },
                    enabledIncrement = hours < 72,
                    enabledDecrement = hours > 0
                )
                
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.spacing_xxl)))
                
                // Minutes Picker
                NumberPicker(
                    value = minutes,
                    label = stringResource(R.string.timer_minutes),
                    onIncrement = { viewModel.updateMinutes(minutes + 5) },
                    onDecrement = { viewModel.updateMinutes(minutes - 5) },
                    enabledIncrement = minutes <= 54, // 59 max, so can't add 5 if > 54
                    enabledDecrement = minutes >= 5
                )
            }
            
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_lg)))
            
            if (!viewModel.isValid()) {
                Text(
                    text = stringResource(R.string.timer_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * A simple number picker using buttons to increase/decrease values.
 * Perfect for quick adjustments without requiring a keyboard.
 */
@Composable
fun NumberPicker(
    value: Int,
    label: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    enabledIncrement: Boolean,
    enabledDecrement: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onIncrement, enabled = enabledIncrement) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.number_picker_increase, label))
        }
        Text(
            text = value.toString().padStart(2, '0'),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onDecrement, enabled = enabledDecrement) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.number_picker_decrease, label))
        }
    }
}
