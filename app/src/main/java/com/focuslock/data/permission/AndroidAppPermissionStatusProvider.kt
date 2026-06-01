package com.focuslock.data.permission

import android.content.Context
import com.focuslock.domain.permission.AppPermissionStatus
import com.focuslock.domain.permission.AppPermissionStatusProvider
import com.focuslock.util.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppPermissionStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : AppPermissionStatusProvider {

    override fun getStatus(): AppPermissionStatus = AppPermissionStatus(
        accessibilityGranted = PermissionChecker.isAccessibilityServiceEnabled(context),
        deviceAdminGranted = PermissionChecker.isDeviceAdminEnabled(context),
        overlayGranted = PermissionChecker.isOverlayPermissionGranted(context),
        batteryOptimizationIgnored = PermissionChecker.isBatteryOptimizationIgnored(context),
        vpnGranted = PermissionChecker.isVpnPermissionGranted(context),
        notificationGranted = PermissionChecker.isNotificationPermissionGranted(context)
    )
}
