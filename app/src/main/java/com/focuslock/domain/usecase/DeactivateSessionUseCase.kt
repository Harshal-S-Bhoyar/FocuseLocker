package com.focuslock.domain.usecase

import com.focuslock.domain.model.SessionStatus
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.SessionWorkerScheduler
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * UseCase to deactivate an active FocusLock session.
 * Ends the session in the database and cancels associated WorkManager timers.
 */
class DeactivateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionWorkerScheduler: SessionWorkerScheduler,
    private val sessionRuntimeController: SessionRuntimeController
) {
    suspend operator fun invoke(sessionId: Long, status: SessionStatus): Result<Unit> {
        return try {
            sessionRepository.endSession(sessionId, status)
            sessionWorkerScheduler.cancelSessionTimers()
            sessionRuntimeController.stopRuntime()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
