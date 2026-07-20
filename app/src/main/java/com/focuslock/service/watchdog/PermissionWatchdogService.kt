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
import kotlinx.coroutines.Job
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

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
    private lateinit var serviceScope: CoroutineScope
    private var pollingJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 4002
        private const val CHANNEL_ID = "watchdog_channel"
        private const val TAG = "SVC_WATCHDOG"

        fun start(context: Context) {
            Timber.tag(TAG).v("start: Starting PermissionWatchdogService")
            val intent = Intent(context, PermissionWatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            Timber.tag(TAG).v("stop: Stopping PermissionWatchdogService")
            val intent = Intent(context, PermissionWatchdogService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.tag(TAG).v("onBind: Binding to service is not supported")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)
        Timber.tag(TAG).v("onCreate: Service created")
        createNotificationChannel()
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
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusLock Protection Active")
            .setContentText("Monitoring permission health...")
            .setSmallIcon(R.drawable.ic_focuslock_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(noOpIntent)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, "Repair Permissions", repairPendingIntent)
            .build()
            
        if (Build.VERSION.SDK_INT >= 34) {
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
        Timber.tag(TAG).v("onStartCommand: Starting polling loop")
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) {
            Timber.tag(TAG).v("startPolling: Already polling, skipping")
            return
        }
        Timber.tag(TAG).v("startPolling: Coroutine launched for health checks")
        pollingJob = serviceScope.launch {
            try {
                Timber.tag(TAG).v("startPolling: Inside launch block, isActive=\$isActive")
                while (isActive) {
                    try {
                    Timber.tag(TAG).v("startPolling: Loop iteration started")
                    val session = sessionRepository.getActiveSessionSync()
                    Timber.tag(TAG).v("startPolling: Session retrieved: \$session")
                    val isExpired = session != null && session.expiresAt <= System.currentTimeMillis()
                    
                    if (session == null || isExpired) {
                        Timber.tag(TAG).v("startPolling: No active session or session expired. Stopping self.")
                        stopSelf()
                        break
                    }
                    
                    var accessibilityOk = PermissionChecker.isAccessibilityServiceEnabled(this@PermissionWatchdogService)
                    
                    // Add retry logic for accessibility check to avoid false positives during heavy load
                    if (!accessibilityOk) {
                        delay(2000L)
                        accessibilityOk = PermissionChecker.isAccessibilityServiceEnabled(this@PermissionWatchdogService)
                    }
                    if (!accessibilityOk) {
                        delay(3000L)
                        accessibilityOk = PermissionChecker.isAccessibilityServiceEnabled(this@PermissionWatchdogService)
                    }
                    
                    val overlayOk = PermissionChecker.isOverlayPermissionGranted(this@PermissionWatchdogService)
                    val deviceAdminOk = PermissionChecker.isDeviceAdminEnabled(this@PermissionWatchdogService)
                    val vpnOk = PermissionChecker.isVpnPermissionGranted(this@PermissionWatchdogService)
                    val allOk = accessibilityOk && overlayOk && deviceAdminOk && vpnOk
                    
                    if (!allOk) {
                        if (!accessibilityOk && overlayOk) {
                            Timber.tag(TAG).w("startPolling: Accessibility revoked! Launching enforcement service.")
                            PermissionEnforcementService.start(this@PermissionWatchdogService)
                        } else {
                            Timber.tag(TAG).e("startPolling: Critical permission(s) revoked: overlay=\$overlayOk, accessibility=\$accessibilityOk, deviceAdmin=\$deviceAdminOk, vpn=\$vpnOk. Session unguardable via overlay")
                            showCriticalFailureNotification()
                            launchPermissionRepairActivity()
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "startPolling: iteration failed")
                }
                
                delay(30_000L)
            }
        } finally {
            pollingJob = null
        }
    }
}

    private fun showCriticalFailureNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val criticalChannel = NotificationChannel(
                "critical_failure_channel",
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(criticalChannel)
        }
        
        val repairIntent = Intent(this, com.focuslock.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "permissions_repair")
        }
        val repairPendingIntent = android.app.PendingIntent.getActivity(
            this, 1, repairIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, "critical_failure_channel")
            .setContentTitle("FocusLock is Compromised")
            .setContentText("Please re-grant required permissions to resume protection.")
            .setSmallIcon(R.drawable.ic_focuslock_secure)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(repairPendingIntent)
            .addAction(0, "Repair", repairPendingIntent)
            .build()
            
        manager.notify(4003, notification)
    }

    private fun launchPermissionRepairActivity() {
        val intent = Intent(this, com.focuslock.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "permissions_repair")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to launch permission repair activity from background")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).v("onDestroy: Service destroyed")
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        Timber.tag(TAG).v("createNotificationChannel: Checking OS version for channel creation")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Timber.tag(TAG).v("createNotificationChannel: Creating watchdog channel")
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
