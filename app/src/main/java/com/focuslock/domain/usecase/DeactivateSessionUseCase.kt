package com.focuslock.domain.usecase
 
import com.focuslock.domain.model.SessionStatus
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.WorkScheduler
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
 
/**
 * UseCase to deactivate an active FocusLock session.
 * Ends the session in the database and cancels associated WorkManager timers.
 */
class DeactivateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val workScheduler: WorkScheduler,
    private val sessionRuntimeController: SessionRuntimeController
) {
    suspend operator fun invoke(sessionId: Long, status: SessionStatus): Result<Unit> {
        Timber.d("DeactivateSessionUseCase: invoke started for sessionId = $sessionId, status = $status")
        return try {
            sessionRepository.endSession(sessionId, status)
            Timber.d("DeactivateSessionUseCase: ended session in repository")
            workScheduler.cancelSessionTimers()
            Timber.d("DeactivateSessionUseCase: cancelled session timers")
            sessionRuntimeController.stopRuntime()
            Timber.i("DeactivateSessionUseCase: Session $sessionId deactivated successfully with status $status")
            Result.success(Unit)
        } catch (e: CancellationException) {
            Timber.w(e, "DeactivateSessionUseCase: Deactivation cancelled")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "DeactivateSessionUseCase: Error deactivating session $sessionId")
            Result.failure(e)
        }
    }
}
