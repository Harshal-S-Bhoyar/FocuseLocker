package com.focuslock.domain.session

data class SessionProtectionStatus(
    val vpnPermissionGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val deviceAdminEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean
)

interface SessionProtectionStatusProvider {
    fun getStatus(): SessionProtectionStatus
}
