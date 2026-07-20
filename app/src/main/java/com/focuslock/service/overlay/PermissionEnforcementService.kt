package com.focuslock.service.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focuslock.R
import com.focuslock.ui.overlay.PermissionEnforcementScreen
import com.focuslock.ui.theme.FocusLockTheme
import com.focuslock.util.OemAutostartHelper
import com.focuslock.util.PermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class PermissionEnforcementService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_START = "com.focuslock.action.START_ENFORCEMENT"
        const val ACTION_STOP = "com.focuslock.action.STOP_ENFORCEMENT"
        private const val NOTIFICATION_ID = 1004
        private const val CHANNEL_ID = "enforcement_overlay_channel"
        private const val TAG = "SVC_ENFORCEMENT"

        fun start(context: Context) {
            if (!com.focuslock.util.PermissionChecker.isOverlayPermissionGranted(context)) {
                Timber.tag(TAG).w("start: Overlay permission not granted. Aborting service start.")
                return
            }
            val intent = Intent(context, PermissionEnforcementService::class.java).apply {
                action = ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            Timber.tag(TAG).d("stop: Stopping PermissionEnforcementService")
            val intent = Intent(context, PermissionEnforcementService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var pollingJob: Job? = null
    @androidx.annotation.VisibleForTesting
    internal var gracePeriodEndTime = 0L

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).d("onCreate: Service created.")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        createNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("onStartCommand: action=${intent?.action}")
        if (intent?.action == ACTION_STOP) {
            hideOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (intent?.action == ACTION_START) {
            showOverlay()
            startPolling()
        }
        
        return START_NOT_STICKY
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        
        Timber.tag(TAG).d("startPolling: Beginning 2s polling loop for accessibility state.")
        pollingJob = serviceScope.launch {
            while (isActive) {
                if (PermissionChecker.isAccessibilityServiceEnabled(this@PermissionEnforcementService)) {
                    Timber.tag(TAG).i("Accessibility re-enabled. Dismissing enforcement overlay.")
                    hideOverlay()
                    stopSelf()
                    break
                } else if (composeView == null && System.currentTimeMillis() > gracePeriodEndTime) {
                    // Check if Accessibility is really disabled before re-showing
                    if (!PermissionChecker.isAccessibilityServiceEnabled(this@PermissionEnforcementService)) {
                        // ponytail: Naive 15s timeout for Settings escape. Ceiling: 15s fixed window allows temporary escape if user ignores Settings. Upgrade path: Use UsageStatsManager to detect when user leaves com.android.settings to instantly reshow overlay.
                        Timber.tag(TAG).i("Grace period expired. Re-showing.")
                        showOverlay()
                    }
                }
                delay(2000L) // Poll every 2 seconds
            }
        }
    }

    private fun showOverlay() {
        if (composeView != null) return
        Timber.tag(TAG).d("showOverlay: Initializing and showing overlay.")
        com.focuslock.service.accessibility.FocusAccessibilityService.settingsGuardMode = false

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PermissionEnforcementService)
            setViewTreeSavedStateRegistryOwner(this@PermissionEnforcementService)
            setViewTreeViewModelStoreOwner(this@PermissionEnforcementService)
            
            setContent {
                FocusLockTheme {
                    PermissionEnforcementScreen(
                        showAutostartOption = OemAutostartHelper.getAutostartIntentsList(this@PermissionEnforcementService).isNotEmpty(),
                        onEnableAccessibility = { openAccessibilitySettings() },
                        onEnableAutostart = { 
                            gracePeriodEndTime = System.currentTimeMillis() + 15_000L
                            com.focuslock.service.accessibility.FocusAccessibilityService.settingsGuardMode = true
                            hideOverlay() // Need to hide overlay so they can interact with settings!
                            OemAutostartHelper.openAutostartSettings(this@PermissionEnforcementService) 
                        }
                    )
                }
            }
        }
        
        composeView?.isFocusableInTouchMode = true
        composeView?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                // Route BACK to home screen
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                true // Intercept back button
            } else {
                false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            Timber.tag(TAG).d("showOverlay: Adding view to WindowManager")
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Unable to show enforcement overlay")
        }
    }

    @androidx.annotation.VisibleForTesting
    internal fun openAccessibilitySettings() {
        Timber.tag(TAG).d("openAccessibilitySettings: Requesting accessibility settings.")
        try {
            gracePeriodEndTime = System.currentTimeMillis() + 15_000L
            com.focuslock.service.accessibility.FocusAccessibilityService.settingsGuardMode = true
            com.focuslock.service.accessibility.FocusAccessibilityService.guardedSettingsPackage = "com.android.settings"
            hideOverlay()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open accessibility settings")
        }
    }

    private fun hideOverlay() {
        Timber.tag(TAG).d("hideOverlay: Attempting to hide overlay.")
        composeView?.let {
            try {
                Timber.tag(TAG).d("hideOverlay: Removing view from WindowManager")
                windowManager.removeView(it)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Unable to remove enforcement overlay")
            }
            composeView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("onDestroy: Service destroyed")
        serviceScope.cancel()
        pollingJob?.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        hideOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.tag(TAG).d("onBind: Binding to service is not supported.")
        return null
    }

    private fun createNotificationChannel() {
        Timber.tag(TAG).d("createNotificationChannel: Setting up notification channel.")
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.enforcement_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.enforcement_notification_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        Timber.tag(TAG).d("createNotification: Building foreground notification.")
        // ponytail: no-op PendingIntent prevents default tap→App Info navigation
        val noOpIntent = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, com.focuslock.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val repairIntent = Intent(this, com.focuslock.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "permissions_repair")
        }
        val repairPendingIntent = android.app.PendingIntent.getActivity(
            this, 1, repairIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.enforcement_overlay_title))
            .setContentText(getString(R.string.enforcement_overlay_description))
            .setSmallIcon(R.drawable.ic_focuslock_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(noOpIntent)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, "Repair Permissions", repairPendingIntent)
            .build()
    }
}
