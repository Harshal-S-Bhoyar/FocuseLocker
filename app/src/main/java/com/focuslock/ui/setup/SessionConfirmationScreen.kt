package com.focuslock.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import com.focuslock.ui.common.CyberpunkLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.R
import com.focuslock.ui.setup.SessionConfirmationViewModel.ActivationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionConfirmationScreen(
    onNavigateToActiveSession: () -> Unit,
    viewModel: SessionConfirmationViewModel = hiltViewModel()
) {
    val activeCategories by viewModel.activeCategories.collectAsStateWithLifecycle()
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    val trustedContact by viewModel.trustedContact.collectAsStateWithLifecycle()
    val activationState by viewModel.activationState.collectAsStateWithLifecycle()

    LaunchedEffect(activationState) {
        if (activationState is ActivationState.Success) onNavigateToActiveSession()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.session_confirmation_title)) })
        },
        bottomBar = {
            if (activationState is ActivationState.Loading) {
                CyberpunkLoadingIndicator(modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.progress_indicator_size)).padding(dimensionResource(R.dimen.spacing_lg)))
            } else {
                com.focuslock.ui.components.PrimaryButton(
                    onClick = viewModel::lockInSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(R.dimen.spacing_lg)),
                    label = stringResource(R.string.session_lock_in)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensionResource(R.dimen.spacing_lg))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_lg))
        ) {
            Text(
                text = stringResource(R.string.session_confirmation_description),
                style = MaterialTheme.typography.bodyLarge
            )

            SummaryCard(stringResource(R.string.session_duration)) {
                val hours = viewModel.durationMillis / (60 * 60 * 1000)
                val minutes = (viewModel.durationMillis % (60 * 60 * 1000)) / (60 * 1000)
                val hoursText = pluralStringResource(R.plurals.session_duration_hours, hours.toInt(), hours)
                val minutesText = pluralStringResource(R.plurals.session_duration_minutes, minutes.toInt(), minutes)
                Text(
                    text = stringResource(R.string.session_duration_value, hoursText, minutesText),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            SummaryCard(stringResource(R.string.session_blocked_categories)) {
                Text(
                    text = if (activeCategories.isEmpty()) {
                        stringResource(R.string.session_none_selected)
                    } else {
                        activeCategories.joinToString(", ") { it.displayName }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            SummaryCard(stringResource(R.string.session_custom_keywords)) {
                Text(
                    text = if (keywords.isEmpty()) {
                        stringResource(R.string.session_none_added)
                    } else {
                        keywords.joinToString(", ") { it.keyword }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            SummaryCard(stringResource(R.string.session_trusted_contact)) {
                Text(
                    text = trustedContact?.email ?: stringResource(R.string.session_not_set),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (activationState is ActivationState.Error) {
                Text(
                    text = (activationState as ActivationState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.spacing_lg))) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            content()
        }
    }
}
