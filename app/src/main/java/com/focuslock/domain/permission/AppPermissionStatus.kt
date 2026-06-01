package com.focuslock.domain.permission

data class AppPermissionStatus(
    val accessibilityGranted: Boolean,
    val deviceAdminGranted: Boolean,
    val overlayGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val vpnGranted: Boolean,
    val notificationGranted: Boolean
)

interface AppPermissionStatusProvider {
    fun getStatus(): AppPermissionStatus
}
