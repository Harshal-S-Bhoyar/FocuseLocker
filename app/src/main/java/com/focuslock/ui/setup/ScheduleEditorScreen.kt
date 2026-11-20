package com.focuslock.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.R
import com.focuslock.data.local.entity.ScheduleWindowEntity
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    onDone: (Boolean) -> Unit,
    showSwitch: Boolean = false,
    viewModel: ScheduleEditorViewModel = hiltViewModel()
) {
    val windows by viewModel.windows.collectAsStateWithLifecycle()
    val enabled by viewModel.scheduleEnabled.collectAsStateWithLifecycle()
    val hasVerifiedContact by viewModel.hasVerifiedContact.collectAsStateWithLifecycle()
    val isContactLoaded by viewModel.isContactLoaded.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var dialogDay by remember { mutableIntStateOf(1) }
    var dialogId by remember { mutableLongStateOf(0L) }
    var dialogOpen by remember { mutableStateOf(false) }
    var startH by remember { mutableIntStateOf(20) }
    var startM by remember { mutableIntStateOf(0) }
    var endH by remember { mutableIntStateOf(13) }
    var endM by remember { mutableIntStateOf(0) }

    fun openAdd(day: Int) {
        dialogId = 0L
        dialogDay = day
        startH = 20
        startM = 0
        endH = 13
        endM = 0
        viewModel.clearError()
        dialogOpen = true
    }

    fun openEdit(w: ScheduleWindowEntity) {
        dialogId = w.id
        dialogDay = w.weekday
        startH = w.startMinutes / 60
        startM = w.startMinutes % 60
        endH = w.endMinutes / 60
        endM = w.endMinutes % 60
        viewModel.clearError()
        dialogOpen = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (showSwitch) R.string.schedule_manage_title
                            else R.string.schedule_editor_title
                        )
                    )
                }
            )
        },
        bottomBar = {
            com.focuslock.ui.components.PrimaryButton(
                label = stringResource(R.string.schedule_done),
                enabled = isContactLoaded,
                onClick = { onDone(hasVerifiedContact) },
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
            if (showSwitch) {
                item(key = "schedule_switch", contentType = "switch") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimensionResource(R.dimen.spacing_lg)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.schedule_enable_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(checked = enabled, onCheckedChange = viewModel::setScheduleEnabled)
                        }
                    }
                }
            }

            if (error != null) {
                item(key = "schedule_error", contentType = "error") {
                    error?.let {
                        Text(
                            text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    }
                }
            }

            if (notice != null) {
                // Lock-in feedback: a running session survived the edit on purpose, so say so
                // in the non-error color instead of letting it read as a failed edit.
                item(key = "schedule_notice", contentType = "notice") {
                    notice?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            for (day in 1..7) {
                val dayWindows = windows.filter { it.weekday == day }
                item(key = "day_$day", contentType = "day") {
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
                            if (dayWindows.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.schedule_no_windows),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            dayWindows.forEach { w ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openEdit(w) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = viewModel.formatWindow(w),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    IconButton(onClick = { viewModel.deleteWindow(w.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.schedule_delete)
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { openAdd(day) }) {
                                Text(stringResource(R.string.schedule_add_window))
                            }
                        }
                    }
                }
            }
        }
    }

    if (dialogOpen) {
        val dialogError by viewModel.error.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = {
                dialogOpen = false
                viewModel.clearError()
            },
            title = { Text(stringResource(R.string.schedule_editor_title)) },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NumberPicker(
                            value = startH,
                            label = stringResource(R.string.schedule_start_hour),
                            onIncrement = { startH = (startH + 1) % 24 },
                            onDecrement = { startH = (startH + 23) % 24 },
                            enabledIncrement = true,
                            enabledDecrement = true
                        )
                        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.spacing_lg)))
                        NumberPicker(
                            value = startM,
                            label = stringResource(R.string.schedule_start_minute),
                            onIncrement = { startM = (startM + 1) % 60 },
                            onDecrement = { startM = (startM + 59) % 60 },
                            enabledIncrement = true,
                            enabledDecrement = true
                        )
                    }
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_lg)))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NumberPicker(
                            value = endH,
                            label = stringResource(R.string.schedule_end_hour),
                            onIncrement = { endH = (endH + 1) % 25; if (endH == 24) endM = 0 },
                            onDecrement = { endH = (endH + 24) % 25; if (endH == 24) endM = 0 },
                            enabledIncrement = true,
                            enabledDecrement = true
                        )
                        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.spacing_lg)))
                        NumberPicker(
                            value = endM,
                            label = stringResource(R.string.schedule_end_minute),
                            onIncrement = { endM = (endM + 1) % 60 },
                            onDecrement = { endM = (endM + 59) % 60 },
                            enabledIncrement = true,
                            enabledDecrement = true
                        )
                    }
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_lg)))
                    TextButton(onClick = {
                        startH = 0
                        startM = 0
                        endH = 24
                        endM = 0
                    }) {
                        Text(stringResource(R.string.schedule_full_day))
                    }
                    if (dialogError != null) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_sm)))
                        dialogError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = viewModel.saveWindow(
                        id = if (dialogId == 0L) null else dialogId,
                        weekday = dialogDay,
                        startMinutes = startH * 60 + startM,
                        endMinutes = endH * 60 + endM
                    )
                    if (ok) {
                        dialogOpen = false
                        viewModel.clearError()
                    }
                }) {
                    Text(stringResource(R.string.schedule_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialogOpen = false
                    viewModel.clearError()
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
