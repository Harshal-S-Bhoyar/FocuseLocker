package com.focuslock.domain.usecase

import com.focuslock.domain.model.SessionStatus
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.SessionWorkerScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * UseCase to activate a new FocusLock session.
 * Enforces the single-active-session rule and schedules WorkManager timers.
 */
class ActivateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionWorkerScheduler: SessionWorkerScheduler,
    private val sessionRuntimeController: SessionRuntimeController,
    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase,
    private val blockingRepository: com.focuslock.data.repository.BlockingRepository
) {
    suspend operator fun invoke(durationMillis: Long): Result<Long> {
        Timber.d("ActivateSessionUseCase: invoke started with duration $durationMillis ms")
        if (durationMillis !in MIN_DURATION_MILLIS..MAX_DURATION_MILLIS) {
            Timber.w("ActivateSessionUseCase: Invalid session duration $durationMillis ms")
            return Result.failure(IllegalArgumentException("Session duration must be between 1 minute and 168 hours."))
        }

        // Clean up any expired session first.
        // If nothing was cleaned AND a live session exists → block creation.
        val cleaned = cleanupStaleSessionUseCase()
        Timber.d("ActivateSessionUseCase: cleanupStaleSessionUseCase returned $cleaned")
        if (!cleaned && sessionRepository.hasActiveSession()) {
            Timber.w("ActivateSessionUseCase: Active session already exists, blocking creation")
            return Result.failure(IllegalStateException("An active session already exists."))
        }

        val expiresAt = System.currentTimeMillis() + durationMillis
        var sessionId: Long? = null
        return try {
            val activeCategories = blockingRepository.getActiveCategoryKeys()
            val createdSessionId = sessionRepository.startSession(expiresAt, activeCategories)
            sessionId = createdSessionId
            Timber.d("ActivateSessionUseCase: started session in repository, sessionId = $sessionId")
            sessionWorkerScheduler.scheduleSessionTimers(expiresAt)
            Timber.d("ActivateSessionUseCase: scheduled session timers")
            sessionRuntimeController.startRuntime(durationMillis)
            Timber.i("ActivateSessionUseCase: Session successfully activated. SessionId: $createdSessionId, expiresAt: $expiresAt")
            Result.success(createdSessionId)
        } catch (e: CancellationException) {
            Timber.w(e, "ActivateSessionUseCase: Activation cancelled. Rolling back.")
            rollbackActivation(sessionId)
            throw e
        } catch (e: Exception) {
            Timber.e(e, "ActivateSessionUseCase: Error activating session. Rolling back.")
            rollbackActivation(sessionId)
            Result.failure(e)
        }
    }

    private suspend fun rollbackActivation(sessionId: Long?) = withContext(NonCancellable) {
        Timber.d("ActivateSessionUseCase: rollbackActivation called for sessionId = $sessionId")
        runCatching { 
            sessionWorkerScheduler.cancelSessionTimers() 
            Timber.d("ActivateSessionUseCase: rollback cancelled session timers")
        }
        runCatching { 
            sessionRuntimeController.stopRuntime() 
            Timber.d("ActivateSessionUseCase: rollback stopped session runtime")
        }
        sessionId?.let { id ->
            runCatching { 
                sessionRepository.endSession(id, SessionStatus.CANCELLED) 
                Timber.d("ActivateSessionUseCase: rollback ended session in repository (status = CANCELLED)")
            }
        }
    }

    private companion object {
        const val MIN_DURATION_MILLIS = 1 * 60 * 1000L
        const val MAX_DURATION_MILLIS = 168 * 60 * 60 * 1000L
    }
}
