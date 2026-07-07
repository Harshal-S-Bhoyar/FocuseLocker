package com.focuslock.service.watchdog

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.focuslock.R
import com.focuslock.data.repository.SessionRepository
import com.focuslock.service.overlay.PermissionEnforcementService
import com.focuslock.util.PermissionChecker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PermissionWatchdogService : Service() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val NOTIFICATION_ID = 4002
        private const val CHANNEL_ID = "watchdog_channel"
        private const val TAG = "SVC_WATCHDOG"

        fun start(context: Context) {
            Timber.tag(TAG).d("start: Starting PermissionWatchdogService")
            val intent = Intent(context, PermissionWatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            Timber.tag(TAG).d("stop: Stopping PermissionWatchdogService")
            val intent = Intent(context, PermissionWatchdogService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.tag(TAG).d("onBind: Binding to service is not supported")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).d("onCreate: Service created")
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusLock Protection Active")
            .setContentText("Monitoring permission health...")
            .setSmallIcon(R.drawable.ic_focuslock_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("onStartCommand: Starting polling loop")
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        Timber.tag(TAG).d("startPolling: Coroutine launched for health checks")
        serviceScope.launch {
            Timber.tag(TAG).d("startPolling: Inside launch block, isActive=\$isActive")
            while (isActive) {
                Timber.tag(TAG).d("startPolling: Loop iteration started")
                val session = sessionRepository.getActiveSessionSync()
                Timber.tag(TAG).d("startPolling: Session retrieved: \$session")
                val isExpired = session != null && session.expiresAt <= System.currentTimeMillis()
                
                if (session == null || isExpired) {
                    Timber.tag(TAG).d("startPolling: No active session or session expired. Stopping self.")
                    stopSelf()
                    break
                }
                
                val accessibilityOk = PermissionChecker.isAccessibilityServiceEnabled(this@PermissionWatchdogService)
                val overlayOk = PermissionChecker.isOverlayPermissionGranted(this@PermissionWatchdogService)
                
                if (!accessibilityOk && overlayOk) {
                    Timber.tag(TAG).w("startPolling: Accessibility revoked! Launching enforcement service.")
                    PermissionEnforcementService.start(this@PermissionWatchdogService)
                }
                
                delay(5_000L)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("onDestroy: Service destroyed")
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        Timber.tag(TAG).d("createNotificationChannel: Checking OS version for channel creation")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Timber.tag(TAG).d("createNotificationChannel: Creating watchdog channel")
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Protection Watchdog",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
