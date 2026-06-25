package com.focuslock.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focuslock.domain.model.SessionStatus
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.usecase.DeactivateSessionUseCase
import com.focuslock.notification.SessionNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A periodic worker that checks if the active session has expired and updates the
 * ongoing notification. If expired, it triggers session deactivation.
 */
@HiltWorker
class SessionTimerWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionRepository: SessionRepository,
    private val deactivateSessionUseCase: DeactivateSessionUseCase,
    private val notificationManager: SessionNotificationManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = sessionRepository.getActiveSessionSync()
        
        when (val decision = SessionWorkerDecision.evaluate(session?.expiresAt, System.currentTimeMillis())) {
            SessionWorkerDecision.NoActiveSession -> notificationManager.cancelNotification()
            SessionWorkerDecision.Expired -> {
                session?.let { deactivateSessionUseCase(it.id, SessionStatus.COMPLETED) }
                notificationManager.cancelNotification()
            }
            is SessionWorkerDecision.Active -> {
                setForeground(notificationManager.getForegroundInfo(decision.remainingMillis))
                notificationManager.updateNotification(decision.remainingMillis)
                
                // --- Health Check ---
                val isAccessibilityAlive = com.focuslock.util.PermissionChecker.isAccessibilityServiceEnabled(context)
                val isOverlayGranted = com.focuslock.util.PermissionChecker.isOverlayPermissionGranted(context)
                if (!isAccessibilityAlive && isOverlayGranted) {
                    com.focuslock.service.overlay.PermissionEnforcementService.start(context)
                }
            }
        }

        Result.success()
    }
}
