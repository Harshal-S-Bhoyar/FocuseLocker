package com.focuslock.ui.overlay

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslock.R
import com.focuslock.service.admin.FocusDeviceAdminReceiver
import com.focuslock.ui.theme.FocusLockTheme
import com.focuslock.util.PermissionChecker
import com.focuslock.service.accessibility.FocusAccessibilityService

class PermissionRepairActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            FocusLockTheme {
                RepairScreen(
                    onEnableOverlay = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        FocusAccessibilityService.settingsGuardMode = true
                        startActivity(intent)
                    },
                    onEnableAccessibility = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        FocusAccessibilityService.settingsGuardMode = true
                        startActivity(intent)
                    },
                    onEnableDeviceAdmin = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(
                                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                ComponentName(this@PermissionRepairActivity, FocusDeviceAdminReceiver::class.java)
                            )
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                getString(R.string.device_admin_description)
                            )
                        }
                        FocusAccessibilityService.settingsGuardMode = true
                        startActivity(intent)
                    },
                    onEnableBattery = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        FocusAccessibilityService.settingsGuardMode = true
                        startActivity(intent)
                    },
                    onEnableVpn = {
                        val prepareIntent = VpnService.prepare(this@PermissionRepairActivity)
                        if (prepareIntent != null) {
                            FocusAccessibilityService.settingsGuardMode = true
                            startActivity(prepareIntent)
                        } else {
                            // If it's null, VPN is already prepared. We can manually trigger a permission check.
                            if (PermissionChecker.isOverlayPermissionGranted(this@PermissionRepairActivity) &&
                                PermissionChecker.isAccessibilityServiceEnabled(this@PermissionRepairActivity) &&
                                PermissionChecker.isDeviceAdminEnabled(this@PermissionRepairActivity) &&
                                PermissionChecker.isVpnPermissionGranted(this@PermissionRepairActivity) &&
                                PermissionChecker.isBatteryOptimizationIgnored(this@PermissionRepairActivity)) {
                                finish()
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FocusAccessibilityService.settingsGuardMode = false
        val allPermissionsOk = PermissionChecker.isOverlayPermissionGranted(this) &&
                PermissionChecker.isAccessibilityServiceEnabled(this) &&
                PermissionChecker.isDeviceAdminEnabled(this) &&
                PermissionChecker.isVpnPermissionGranted(this) &&
                PermissionChecker.isBatteryOptimizationIgnored(this)

        if (allPermissionsOk) {
            finish()
        }
    }


    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, PermissionRepairActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }
}

@Composable
fun RepairScreen(
    onEnableOverlay: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableDeviceAdmin: () -> Unit,
    onEnableBattery: () -> Unit,
    onEnableVpn: () -> Unit
) {
    // ponytail: Swallows back navigation to prevent lockdown escape (Android 13+ compatible).
    BackHandler {
        // Do nothing. The user must fix permissions.
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SECURITY BREACH DETECTED",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Critical permissions were revoked. This device is locked until permissions are restored.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = onEnableOverlay,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("1. Restore Overlay Permission")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onEnableAccessibility,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("2. Restore Accessibility")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onEnableDeviceAdmin,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("3. Restore Device Admin")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onEnableBattery,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("4. Disable Battery Optimization")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onEnableVpn,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("5. Restore VPN")
            }
        }
    }
}
