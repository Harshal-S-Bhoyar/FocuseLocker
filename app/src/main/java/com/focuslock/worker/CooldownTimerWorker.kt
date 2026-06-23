package com.focuslock.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focuslock.domain.model.SessionStatus
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.usecase.DeactivateSessionUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A one-time worker scheduled to run when the 10-minute cooldown ends.
 * Ensures the session is properly deactivated.
 */
@HiltWorker
class CooldownTimerWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionRepository: SessionRepository,
    private val deactivateSessionUseCase: DeactivateSessionUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = sessionRepository.getActiveSessionSync()
        if (session != null) {
            deactivateSessionUseCase(session.id, SessionStatus.COMPLETED)
        }
        Result.success()
    }
}
