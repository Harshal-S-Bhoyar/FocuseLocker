package com.focuslock.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.focuslock.ui.components.PrimaryButton
import com.focuslock.ui.components.PrimaryButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.focuslock.R

@Composable
fun BlockingOverlayScreen(
    matchedTerm: String,
    blockReason: String,
    timeRemainingMillis: Long,
    onReturnToApp: () -> Unit,
    onRequestEarlyUnlock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(dimensionResource(R.dimen.spacing_xl)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.overlay_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_lg)))
        Text(
            text = stringResource(R.string.overlay_blocked_term, matchedTerm),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_sm)))
        Text(
            text = stringResource(R.string.overlay_reason, blockReason),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_xxl)))
        PrimaryButton(
            label = stringResource(R.string.overlay_return),
            onClick = onReturnToApp,
            modifier = Modifier.padding(bottom = dimensionResource(R.dimen.spacing_lg))
        )
        PrimaryButton(
            label = stringResource(R.string.active_session_request_unlock),
            onClick = onRequestEarlyUnlock
        )
    }
}
