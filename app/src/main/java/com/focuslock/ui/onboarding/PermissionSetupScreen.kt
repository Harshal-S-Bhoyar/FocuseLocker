package com.focuslock.ui.onboarding

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.R
import com.focuslock.service.admin.FocusDeviceAdminReceiver

@Composable
fun PermissionSetupScreen(
    onNavigateNext: () -> Unit,
    viewModel: PermissionSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Refresh permissions every time we return to this screen
    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    LaunchedEffect(state.navigateToTrustedContact) {
        if (state.navigateToTrustedContact) {
            viewModel.onNavigationConsumed()
            onNavigateNext()
        }
    }

    // Launchers for system settings screens
    val accessibilityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    PermissionSetupContent(
        permissions = state.permissions,
        onGrantAccessibility = {
            accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onGrantDeviceAdmin = {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(context, FocusDeviceAdminReceiver::class.java)
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.device_admin_description)
                )
            }
            deviceAdminLauncher.launch(intent)
        },
        onGrantOverlay = {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        },
        onGrantBattery = {
            batteryLauncher.launch(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        },
        onGrantVpn = {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent == null) {
                viewModel.refreshPermissions()
            } else {
                vpnLauncher.launch(prepareIntent)
            }
        },
        onGrantNotification = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onContinue = viewModel::onContinueClicked
    )
}

@Composable
private fun PermissionSetupContent(
    permissions: PermissionState,
    onGrantAccessibility: () -> Unit,
    onGrantDeviceAdmin: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantBattery: () -> Unit,
    onGrantVpn: () -> Unit,
    onGrantNotification: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = dimensionResource(R.dimen.spacing_xl),
                vertical = dimensionResource(R.dimen.spacing_xxl)
            ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_lg))
    ) {
        Text(
            text = stringResource(R.string.permission_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = stringResource(R.string.permission_setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_sm)))

        // Critical permissions
        PermissionRow(
            title = stringResource(R.string.permission_accessibility_title),
            description = stringResource(R.string.permission_accessibility_description),
            isCritical = true,
            isGranted = permissions.accessibilityGranted,
            onGrant = onGrantAccessibility
        )

        PermissionRow(
            title = stringResource(R.string.permission_device_admin_title),
            description = stringResource(R.string.permission_device_admin_description),
            isCritical = true,
            isGranted = permissions.deviceAdminGranted,
            onGrant = onGrantDeviceAdmin
        )

        PermissionRow(
            title = stringResource(R.string.permission_overlay_title),
            description = stringResource(R.string.permission_overlay_description),
            isCritical = true,
            isGranted = permissions.overlayGranted,
            onGrant = onGrantOverlay
        )

        // Optional permissions
        PermissionRow(
            title = stringResource(R.string.permission_battery_title),
            description = stringResource(R.string.permission_battery_description),
            isCritical = false,
            isGranted = permissions.batteryOptimizationIgnored,
            onGrant = onGrantBattery
        )

        PermissionRow(
            title = stringResource(R.string.permission_vpn_title),
            description = stringResource(R.string.permission_vpn_description),
            isCritical = true,
            isGranted = permissions.vpnGranted,
            onGrant = onGrantVpn
        )

        PermissionRow(
            title = stringResource(R.string.permission_notification_title),
            description = stringResource(R.string.permission_notification_description),
            isCritical = false,
            isGranted = permissions.notificationGranted,
            onGrant = onGrantNotification
        )

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_sm)))

        Button(
            onClick = onContinue,
            enabled = permissions.allCriticalGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.button_height))
        ) {
            Text(
                text = if (permissions.allCriticalGranted)
                    stringResource(R.string.permission_continue)
                else
                    stringResource(R.string.permission_grant_required),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isCritical: Boolean,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(dimensionResource(R.dimen.spacing_lg)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_md))
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(dimensionResource(R.dimen.spacing_xl)),
                tint = if (isGranted)
                    MaterialTheme.colorScheme.primary
                else if (isCritical)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_sm)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCritical) {
                        Text(
                            text = stringResource(R.string.permission_required_badge),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (!isGranted) {
                OutlinedButton(onClick = onGrant) {
                    Text(text = stringResource(R.string.permission_grant_button))
                }
            }
        }
    }
}
