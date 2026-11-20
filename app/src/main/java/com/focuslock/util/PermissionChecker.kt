package com.focuslock.util
import android.Manifest
import kotlinx.coroutines.delay
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.focuslock.service.accessibility.FocusAccessibilityService
import com.focuslock.service.admin.FocusDeviceAdminReceiver

/**
 * Stateless utility — checks real-time permission state.
 * Called from PermissionSetupViewModel only. No logic in composables.
 */
object PermissionChecker {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val running = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any {
            it.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
        if (running) return true
        // Fallback: Secure settings string. Some OEM skins lag or empty the manager
        // list after the user enables the service, while the committed setting is current.
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = ComponentName(context, FocusAccessibilityService::class.java)
        return flat.split(':').any { entry ->
            val component = ComponentName.unflattenFromString(entry) ?: return@any false
            if (component.packageName != target.packageName) return@any false
            normalizeClassName(component.packageName, component.className) == target.className
        }
    }

    private fun normalizeClassName(pkg: String, cls: String): String = when {
        cls.startsWith(".") -> pkg + cls
        "." !in cls -> "$pkg.$cls"
        else -> cls
    }



    fun isDeviceAdminEnabled(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, FocusDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * VPN permission is checked by attempting to prepare — returns null if already granted.
     * We can't check this without an Activity context, so we track it in AppSettings instead.
     */
    fun isVpnPermissionGranted(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }


}
