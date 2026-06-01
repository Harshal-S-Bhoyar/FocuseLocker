package com.focuslock.service.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
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
import com.focuslock.MainActivity
import com.focuslock.R
import com.focuslock.ui.overlay.BlockingOverlayScreen
import com.focuslock.ui.theme.FocusLockTheme

/**
 * BlockingOverlayService manages the full-screen overlay UI that is shown to the user
 * when they attempt to browse a blocked URL, block keyword, or access protected system
 * settings screens (like settings, app manager) during an active FocusLock session.
 */
class BlockingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val EXTRA_MATCHED_TERM = OverlayContract.EXTRA_MATCHED_TERM
        const val EXTRA_BLOCK_REASON = OverlayContract.EXTRA_BLOCK_REASON
        const val EXTRA_TIME_REMAINING = OverlayContract.EXTRA_TIME_REMAINING
        const val ACTION_SHOW_OVERLAY = OverlayContract.ACTION_SHOW_OVERLAY
        const val ACTION_HIDE_OVERLAY = OverlayContract.ACTION_HIDE_OVERLAY
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "blocking_overlay_channel"
        private const val TAG = "BlockingOverlayService"
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    
    // Lifecycle components needed for ComposeView
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE_OVERLAY) {
            hideOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (intent?.action == ACTION_SHOW_OVERLAY) {
            val matchedTerm = intent.getStringExtra(EXTRA_MATCHED_TERM) ?: "Unknown"
            val blockReason = intent.getStringExtra(EXTRA_BLOCK_REASON) ?: "Unknown"
            val timeRemaining = intent.getLongExtra(EXTRA_TIME_REMAINING, 0L)
            
            showOverlay(matchedTerm, blockReason, timeRemaining)
        }
        
        return START_NOT_STICKY
    }

    private fun showOverlay(matchedTerm: String, blockReason: String, timeRemaining: Long) {
        if (composeView != null) {
            // Update existing view if it is already shown
            composeView?.setContent {
                FocusLockTheme {
                    BlockingOverlayScreen(
                        matchedTerm = matchedTerm,
                        blockReason = blockReason,
                        timeRemainingMillis = timeRemaining,
                        onReturnToApp = { openMainActivity() },
                        onRequestEarlyUnlock = { openUnlockFlow() }
                    )
                }
            }
            return
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BlockingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@BlockingOverlayService)
            setViewTreeViewModelStoreOwner(this@BlockingOverlayService)
            
            setContent {
                FocusLockTheme {
                    BlockingOverlayScreen(
                        matchedTerm = matchedTerm,
                        blockReason = blockReason,
                        timeRemainingMillis = timeRemaining,
                        onReturnToApp = { openMainActivity() },
                        onRequestEarlyUnlock = { openUnlockFlow() }
                    )
                }
            }
        }
        
        // Ensure intercepting back button
        composeView?.isFocusableInTouchMode = true
        composeView?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                // Intercept back button and do nothing to prevent dismissal
                true
            } else {
                false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        // Remove FLAG_NOT_FOCUSABLE to receive key events (like BACK button)
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to show blocking overlay", e)
        }
    }

    private fun hideOverlay() {
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to remove blocking overlay", e)
            }
            composeView = null
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        // Auto-dismiss logic should be handled by accessibility service detecting URL change
    }
    
    private fun openUnlockFlow() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "early_unlock") // Let the activity route correctly
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        hideOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.overlay_notification_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_focuslock_blocking)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
