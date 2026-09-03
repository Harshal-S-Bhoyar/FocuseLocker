package com.focuslock.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focuslock.data.repository.SessionRepository
import com.focuslock.util.PermissionChecker
import com.focuslock.service.overlay.PermissionEnforcementService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class AccessibilityHealthWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionRepository: SessionRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val session = sessionRepository.getActiveSessionSync()
            val isActiveSession = session != null && session.expiresAt > System.currentTimeMillis()
            
            if (!isActiveSession) return Result.success()
            
            var isAccessibilityAlive = PermissionChecker.isAccessibilityServiceEnabled(context)
            
            // Retry once with 2s delay — OS may be rebinding after memory pressure
            if (!isAccessibilityAlive) {
                kotlinx.coroutines.delay(2000L)
                isAccessibilityAlive = PermissionChecker.isAccessibilityServiceEnabled(context)
            }
            // Add a second retry to account for longer system stalls during heavy memory pressure
            if (!isAccessibilityAlive) {
                kotlinx.coroutines.delay(3000L)
                isAccessibilityAlive = PermissionChecker.isAccessibilityServiceEnabled(context)
            }
            
            val isOverlayGranted = PermissionChecker.isOverlayPermissionGranted(context)
            val isDeviceAdminOk = PermissionChecker.isDeviceAdminEnabled(context)
            val isVpnOk = PermissionChecker.isVpnPermissionGranted(context)
            val allOk = isAccessibilityAlive && isOverlayGranted && isDeviceAdminOk && isVpnOk

            if (!allOk) {
                if (!isAccessibilityAlive && isOverlayGranted) {
                    Timber.w("AccessibilityHealthWorker: Active session detected but Accessibility is DEAD. Launching enforcement overlay.")
                    PermissionEnforcementService.start(context)
                } else {
                    Timber.w("AccessibilityHealthWorker: Critical permission(s) lost. overlay=$isOverlayGranted accessibility=$isAccessibilityAlive. Launching repair activity.")
                    try {
                        val intent = android.content.Intent(context, com.focuslock.MainActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("navigate_to", "permissions_repair")
                        }
                        val pendingIntent = android.app.PendingIntent.getActivity(
                            context,
                            0,
                            intent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        notificationManager.createNotificationChannel(
                            android.app.NotificationChannel(
                                "focuslock_critical",
                                "Critical Alerts",
                                android.app.NotificationManager.IMPORTANCE_HIGH
                            )
                        )
                        val notification = androidx.core.app.NotificationCompat.Builder(context, "focuslock_critical")
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setContentTitle("FocusLock Protection Stopped")
                            .setContentText("Critical permissions lost. Tap to repair.")
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .build()
                        notificationManager.notify(1001, notification)
                    } catch (e: Exception) {
                        Timber.e(e, "AccessibilityHealthWorker: Failed to show repair notification")
                    }
                }
            } else {
                Timber.d("AccessibilityHealthWorker: Health check passed. all permissions OK.")
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "AccessibilityHealthWorker: Failed to perform health check")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "accessibility_health_worker"
    }
}
