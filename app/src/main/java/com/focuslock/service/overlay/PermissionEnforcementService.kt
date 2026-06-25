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

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).d("onCreate: Service created.")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
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
                }
                delay(2000L) // Poll every 2 seconds
            }
        }
    }

    private fun showOverlay() {
        if (composeView != null) return

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PermissionEnforcementService)
            setViewTreeSavedStateRegistryOwner(this@PermissionEnforcementService)
            setViewTreeViewModelStoreOwner(this@PermissionEnforcementService)
            
            setContent {
                FocusLockTheme {
                    PermissionEnforcementScreen(
                        showAutostartOption = OemAutostartHelper.getAutostartIntent(this@PermissionEnforcementService) != null,
                        onEnableAccessibility = { openAccessibilitySettings() },
                        onEnableAutostart = { OemAutostartHelper.openAutostartSettings(this@PermissionEnforcementService) }
                    )
                }
            }
        }
        
        composeView?.isFocusableInTouchMode = true
        composeView?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
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

    private fun openAccessibilitySettings() {
        try {
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

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.enforcement_overlay_title))
            .setContentText(getString(R.string.enforcement_overlay_description))
            .setSmallIcon(R.drawable.ic_focuslock_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
