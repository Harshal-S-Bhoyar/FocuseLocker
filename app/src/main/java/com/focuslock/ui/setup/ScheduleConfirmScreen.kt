package com.focuslock.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleConfirmScreen(
    onDone: (Long?) -> Unit,
    viewModel: ScheduleEditorViewModel = hiltViewModel()
) {
    val windows by viewModel.windows.collectAsStateWithLifecycle()
    // Arming can fail (enable, or enqueuing the scheduled checks) — without this the failure was
    // only logged and the screen looked identical to a successful arm.
    val error by viewModel.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isArming by remember { mutableStateOf(false) }
    // ponytail: terminal confirmation state when weekly schedule is armed for future
    var isConfirmedArmed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.schedule_confirm_title)) })
        },
        bottomBar = {
            com.focuslock.ui.components.PrimaryButton(
                label = stringResource(if (isConfirmedArmed) R.string.schedule_confirm_title else R.string.schedule_done),
                enabled = !isArming && !isConfirmedArmed,
                onClick = {
                    if (!isConfirmedArmed) {
                        isArming = true
                        scope.launch {
                            try {
                                // The outcome decides the screen: an arming failure must not be
                                // rendered as the armed confirmation, or the user is left on a
                                // success state with the retry button disabled.
                                when (val outcome = viewModel.armScheduleAndCheckActivation()) {
                                    // Nothing was armed; the error text above the windows says why
                                    // and the button stays enabled so the user can try again.
                                    ScheduleArmOutcome.Failed -> Unit
                                    // A duplicate tap while the first arming call is still running:
                                    // it changed nothing, so the screen stays exactly as it is.
                                    ScheduleArmOutcome.ArmInFlight -> Unit
                                    ScheduleArmOutcome.ArmedIdle -> {
                                        isConfirmedArmed = true
                                        onDone(null)
                                    }
                                    // Armed, but the immediate start failed: like Failed, stay on
                                    // this screen with Done available so the user can retry now
                                    // instead of waiting for the next periodic check. A live
                                    // session never lands here.
                                    ScheduleArmOutcome.ActivationFailed -> Unit
                                    // Live window: hand off to the session confirmation screen.
                                    is ScheduleArmOutcome.LiveWindow -> onDone(outcome.remainingMillis)
                                }
                            } finally {
                                isArming = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.spacing_lg))
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(dimensionResource(R.dimen.spacing_lg)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_lg))
        ) {
            if (isConfirmedArmed) {
                item(key = "confirmed_armed_banner") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(dimensionResource(R.dimen.spacing_lg))) {
                            Text(
                                text = stringResource(R.string.schedule_confirm_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.schedule_confirm_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.schedule_confirm_description),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            error?.let { message ->
                item(key = "confirm_error") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            for (day in 1..7) {
                val dayWindows = windows.filter { it.weekday == day }
                if (dayWindows.isNotEmpty()) {
                    item(key = "confirm_day_$day") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(dimensionResource(R.dimen.spacing_lg))) {
                                Text(
                                    text = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                dayWindows.forEach { w ->
                                    Text(
                                        text = viewModel.formatWindow(w),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
