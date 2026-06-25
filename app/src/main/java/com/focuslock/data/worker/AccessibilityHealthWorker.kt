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
            val isAccessibilityAlive = PermissionChecker.isAccessibilityServiceEnabled(context)
            val isOverlayGranted = PermissionChecker.isOverlayPermissionGranted(context)

            if (isActiveSession && !isAccessibilityAlive && isOverlayGranted) {
                Timber.w("AccessibilityHealthWorker: Active session detected but Accessibility is DEAD. Launching enforcement overlay.")
                PermissionEnforcementService.start(context)
            } else {
                Timber.d("AccessibilityHealthWorker: Health check passed. isActiveSession=$isActiveSession, isAccessibilityAlive=$isAccessibilityAlive")
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
