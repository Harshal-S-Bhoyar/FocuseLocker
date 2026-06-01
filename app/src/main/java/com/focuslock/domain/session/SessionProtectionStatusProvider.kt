package com.focuslock.domain.session

data class SessionProtectionStatus(
    val vpnPermissionGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val deviceAdminEnabled: Boolean
)

interface SessionProtectionStatusProvider {
    fun getStatus(): SessionProtectionStatus
}
