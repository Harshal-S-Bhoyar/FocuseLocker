package com.focuslock.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber

object OemAutostartHelper {
    private val MANUFACTURERS = listOf(
        "xiaomi", "poco", "oppo", "vivo", "letv", "honor", "huawei", "oneplus", "realme", "samsung", "asus"
    )

    fun needsAutostart(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return MANUFACTURERS.any { manufacturer.contains(it) }
    }

    fun getAutostartIntent(context: Context): Intent? {
        val intent = Intent()
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("poco") -> {
                intent.component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                intent.component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }
            manufacturer.contains("vivo") -> {
                intent.component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
            manufacturer.contains("letv") -> {
                intent.component = ComponentName(
                    "com.letv.android.letvsafe",
                    "com.letv.android.letvsafe.AutobootManageActivity"
                )
            }
            manufacturer.contains("honor") || manufacturer.contains("huawei") -> {
                intent.component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }
            manufacturer.contains("oneplus") -> {
                intent.component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            }
            manufacturer.contains("samsung") -> {
                intent.component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
            manufacturer.contains("asus") -> {
                intent.component = ComponentName(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.autostart.AutoStartActivity"
                )
            }
            else -> return null
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        
        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            null
        }
    }

    fun openAutostartSettings(context: Context) {
        try {
            val intent = getAutostartIntent(context)
            if (intent != null) {
                Timber.d("OemAutostartHelper: Resolved autostart intent for ${Build.MANUFACTURER}. Launching.")
                context.startActivity(intent)
            } else {
                Timber.d("OemAutostartHelper: No specific autostart intent found for ${Build.MANUFACTURER}. Falling back to App Settings.")
                openAppSettings(context)
            }
        } catch (e: Exception) {
            Timber.e(e, "OemAutostartHelper: Failed to open autostart settings. Falling back.")
            openAppSettings(context)
        }
    }

    private fun openAppSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Timber.d("OemAutostartHelper: Opened Application Details Settings.")
        } catch (e: Exception) {
            Timber.e(e, "OemAutostartHelper: Failed to open Application Details Settings.")
        }
    }
}
