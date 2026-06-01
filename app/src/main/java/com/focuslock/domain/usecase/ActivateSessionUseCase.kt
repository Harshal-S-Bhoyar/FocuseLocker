package com.focuslock.domain.usecase

import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.session.SessionRuntimeController
import com.focuslock.domain.worker.SessionWorkerScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UseCase to activate a new FocusLock session.
 * Enforces the single-active-session rule and schedules WorkManager timers.
 */
class ActivateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionWorkerScheduler: SessionWorkerScheduler,
    private val sessionRuntimeController: SessionRuntimeController
) {
    suspend operator fun invoke(durationMillis: Long): Result<Long> {
        if (durationMillis !in MIN_DURATION_MILLIS..MAX_DURATION_MILLIS) {
            return Result.failure(IllegalArgumentException("Session duration must be between 15 minutes and 72 hours."))
        }

        if (sessionRepository.hasActiveSession()) {
            return Result.failure(IllegalStateException("An active session already exists."))
        }

        val expiresAt = System.currentTimeMillis() + durationMillis
        var sessionId: Long? = null
        return try {
            val createdSessionId = sessionRepository.startSession(expiresAt)
            sessionId = createdSessionId
            sessionWorkerScheduler.scheduleSessionTimers(expiresAt)
            sessionRuntimeController.startRuntime(durationMillis)
            Result.success(createdSessionId)
        } catch (e: CancellationException) {
            rollbackActivation(sessionId)
            throw e
        } catch (e: Exception) {
            rollbackActivation(sessionId)
            Result.failure(e)
        }
    }

    private suspend fun rollbackActivation(sessionId: Long?) = withContext(NonCancellable) {
        runCatching { sessionWorkerScheduler.cancelSessionTimers() }
        runCatching { sessionRuntimeController.stopRuntime() }
        sessionId?.let { runCatching { sessionRepository.endSession(it, SessionStatus.CANCELLED) } }
    }

    private companion object {
        const val MIN_DURATION_MILLIS = 15 * 60 * 1000L
        const val MAX_DURATION_MILLIS = 72 * 60 * 60 * 1000L
    }
}
