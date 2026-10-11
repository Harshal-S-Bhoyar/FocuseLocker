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
    private val blockingRepository: com.focuslock.data.repository.BlockingRepository,
    private val appBlockSelectionStore: com.focuslock.data.apps.AppBlockSelectionStore
) {
    suspend operator fun invoke(durationMillis: Long): Result<Long> {
        if (durationMillis !in MIN_DURATION_MILLIS..MAX_DURATION_MILLIS) {
            Timber.w("ActivateSessionUseCase: Invalid session duration $durationMillis ms")
            return Result.failure(IllegalArgumentException("Session duration must be between 1 minute and 168 hours."))
        }

        // Clean up any expired session first.
        // If nothing was cleaned AND a live session exists → block creation.
        val cleaned = cleanupStaleSessionUseCase()
        if (!cleaned && sessionRepository.hasActiveSession()) {
            Timber.w("ActivateSessionUseCase: Active session already exists, blocking creation")
            return Result.failure(IllegalStateException("An active session already exists."))
        }

        val expiresAt = System.currentTimeMillis() + durationMillis
        var sessionId: Long? = null
        return try {
            val activeCategories = blockingRepository.getActiveCategoryKeys()
            val blockedApps = appBlockSelectionStore.selected.value.toList()
            val createdSessionId = sessionRepository.startSession(expiresAt, activeCategories, blockedApps)
            sessionId = createdSessionId
            sessionWorkerScheduler.scheduleSessionTimers(expiresAt)
            sessionRuntimeController.startRuntime(durationMillis)
            // Clear only after everything succeeded so a scheduler/runtime failure preserves selection for retry
            appBlockSelectionStore.clear()
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
        runCatching { 
            sessionWorkerScheduler.cancelSessionTimers() 
        }
        runCatching { 
            sessionRuntimeController.stopRuntime() 
        }
        sessionId?.let { id ->
            runCatching { 
                sessionRepository.endSession(id, SessionStatus.CANCELLED) 
            }
        }
    }

    private companion object {
        const val MIN_DURATION_MILLIS = 1 * 60 * 1000L
        const val MAX_DURATION_MILLIS = 168 * 60 * 60 * 1000L
    }
}
