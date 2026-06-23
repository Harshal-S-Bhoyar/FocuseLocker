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
 * A one-time worker scheduled to run exactly when the session expires.
 * Ensures the session is properly deactivated.
 */
@HiltWorker
class SessionExpiryWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionRepository: SessionRepository,
    private val deactivateSessionUseCase: DeactivateSessionUseCase,
    private val notificationManager: SessionNotificationManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        timber.log.Timber.i("SessionExpiryWorker started.")
        val session = sessionRepository.getActiveSessionSync()
        if (session != null) {
            timber.log.Timber.i("Deactivating expired session ${session.id}")
            setForeground(notificationManager.getForegroundInfo(0L))
            deactivateSessionUseCase(session.id, SessionStatus.COMPLETED)
            notificationManager.cancelNotification()
        }
        Result.success()
    }
}
