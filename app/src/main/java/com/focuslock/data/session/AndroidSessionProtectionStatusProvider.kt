package com.focuslock.data.session

import android.content.Context
import com.focuslock.domain.session.SessionProtectionStatus
import com.focuslock.domain.session.SessionProtectionStatusProvider
import com.focuslock.util.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSessionProtectionStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : SessionProtectionStatusProvider {

    override fun getStatus(): SessionProtectionStatus = SessionProtectionStatus(
        vpnPermissionGranted = PermissionChecker.isVpnPermissionGranted(context),
        accessibilityEnabled = PermissionChecker.isAccessibilityServiceEnabled(context),
        deviceAdminEnabled = PermissionChecker.isDeviceAdminEnabled(context),
        batteryOptimizationIgnored = PermissionChecker.isBatteryOptimizationIgnored(context)
    )
}
