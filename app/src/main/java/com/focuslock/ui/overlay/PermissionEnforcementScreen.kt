package com.focuslock.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.focuslock.ui.components.PrimaryButton

@Composable
fun PermissionEnforcementScreen(
    showAutostartOption: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableAutostart: () -> Unit
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
            text = stringResource(R.string.enforcement_overlay_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_lg)))
        Text(
            text = stringResource(R.string.enforcement_overlay_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_xxl)))
        
        PrimaryButton(
            label = stringResource(R.string.enforcement_overlay_enable),
            onClick = onEnableAccessibility,
            modifier = Modifier.padding(bottom = dimensionResource(R.dimen.spacing_lg))
        )
        
        if (showAutostartOption) {
            PrimaryButton(
                label = stringResource(R.string.enforcement_overlay_autostart),
                onClick = onEnableAutostart
            )
        }
    }
}
