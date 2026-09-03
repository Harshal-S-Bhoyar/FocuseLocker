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
        Timber.tag("OemAutostartHelper").v("needsAutostart: Checking for manufacturer")
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        return MANUFACTURERS.any { manufacturer.contains(it) }
    }

    @Deprecated("Use getAutostartIntentsList instead", ReplaceWith("getAutostartIntentsList(context).firstOrNull()"))
    fun getAutostartIntent(context: Context): Intent? {
        return getAutostartIntentsList(context).firstOrNull()
    }

    private fun populateComponents(manufacturer: String, components: MutableList<ComponentName>) {
        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("poco") -> {
                components.add(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                components.add(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
                components.add(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"))
                components.add(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
            }
            manufacturer.contains("vivo") -> {
                components.add(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
                components.add(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
                components.add(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"))
            }
            manufacturer.contains("letv") -> {
                components.add(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"))
            }
            manufacturer.contains("honor") || manufacturer.contains("huawei") -> {
                components.add(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
                components.add(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
                components.add(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
            }
            manufacturer.contains("oneplus") -> {
                components.add(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"))
            }
            manufacturer.contains("samsung") -> {
                components.add(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                components.add(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                components.add(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                components.add(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.AppSleepListActivity"))
                components.add(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.UnusedAppsListActivity"))
            }
            manufacturer.contains("asus") -> {
                components.add(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"))
                components.add(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"))
            }
        }
    }

    init {
        // Just defining the function, actual population happens below.
    }

    fun getAutostartIntentsList(context: Context): List<Intent> {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val components = mutableListOf<ComponentName>()
        val intents = mutableListOf<Intent>()
        
        populateComponents(manufacturer, components)

        for (component in components) {
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                intents.add(intent)
            }
        }
        return intents
    }

    fun openAutostartSettings(context: Context) {
        val intents = getAutostartIntentsList(context)
        for (intent in intents) {
            try {
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Timber.d("OemAutostartHelper: Resolved autostart intent for ${Build.MANUFACTURER}. Launching: ${intent.component}")
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Timber.e(e, "OemAutostartHelper: Failed to open autostart settings for ${intent.component}")
            }
        }
        Timber.d("OemAutostartHelper: No specific autostart intent worked for ${Build.MANUFACTURER}. Falling back to App Settings.")
        openAppSettings(context)
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
