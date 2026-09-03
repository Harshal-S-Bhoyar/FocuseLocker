package com.focuslock.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import timber.log.Timber

object OemBatteryOptimizationHelper {

    @android.annotation.SuppressLint("BatteryLife")
    fun getBatteryOptimizationIntentsList(context: Context): List<Intent> {
        val intents = mutableListOf<Intent>()
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val components = mutableListOf<ComponentName>()
        
        when {
            manufacturer.contains("samsung") -> {
                components.add(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                components.add(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                components.add(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                components.add(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.AppSleepListActivity"))
                components.add(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.UnusedAppsListActivity"))
            }
            manufacturer.contains("xiaomi") || manufacturer.contains("poco") -> {
                components.add(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                components.add(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            }
        }

        for (component in components) {
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (manufacturer.contains("xiaomi") || manufacturer.contains("poco")) {
                    putExtra("package_name", context.packageName)
                    putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
                }
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                intents.add(intent)
            }
        }

        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (requestIntent.resolveActivity(context.packageManager) != null) {
            intents.add(requestIntent)
        }

        val standardIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (standardIntent.resolveActivity(context.packageManager) != null) {
            intents.add(standardIntent)
        }

        return intents
    }

    @Deprecated("Use getBatteryOptimizationIntentsList instead", ReplaceWith("getBatteryOptimizationIntentsList(context).firstOrNull()"))
    fun getBatteryOptimizationIntent(context: Context): Intent? {
        return getBatteryOptimizationIntentsList(context).firstOrNull()
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intents = getBatteryOptimizationIntentsList(context)
        for (intent in intents) {
            try {
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Timber.d("OemBatteryOptimizationHelper: Launching battery settings intent: ${intent.component ?: intent.action}")
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Timber.e(e, "OemBatteryOptimizationHelper: Failed to open battery settings intent: ${intent.component ?: intent.action}")
            }
        }
        
        Timber.d("OemBatteryOptimizationHelper: No battery settings intent worked. Falling back to App Info.")
        openAppSettings(context)
    }

    private fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Timber.d("OemBatteryOptimizationHelper: Opened Application Details Settings.")
        } catch (e: Exception) {
            Timber.e(e, "OemBatteryOptimizationHelper: Failed to open Application Details Settings.")
        }
    }
}
