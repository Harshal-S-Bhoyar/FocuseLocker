package com.focuslock.domain.usecase

import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.WorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Outcome of an activation request. [AlreadyActive] is a first-class outcome, not an error:
 * a healthy running session is a state callers hand off to, and folding the check in here
 * removes the copy of the rule that every caller used to keep (and the TOCTOU gap between
 * a caller's `hasActiveSession()` check and this insert).
 */
sealed interface ActivationResult {
    data class Activated(val sessionId: Long, val expiresAt: Long) : ActivationResult
    data class AlreadyActive(val session: Session) : ActivationResult
    data class Failed(val error: Throwable) : ActivationResult
}

/**
 * UseCase to activate a new FocusLock session.
 * Enforces the single-active-session rule and schedules WorkManager timers.
 */
class ActivateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val workScheduler: WorkScheduler,
    private val sessionRuntimeController: SessionRuntimeController,
    private val cleanupStaleSessionUseCase: CleanupStaleSessionUseCase,
    private val blockingRepository: com.focuslock.data.repository.BlockingRepository,
    private val appBlockSelectionStore: com.focuslock.data.apps.AppBlockSelectionStore
) {
    suspend operator fun invoke(durationMillis: Long): ActivationResult {
        if (durationMillis !in MIN_DURATION_MILLIS..MAX_DURATION_MILLIS) {
            Timber.w("ActivateSessionUseCase: Invalid session duration $durationMillis ms")
            return ActivationResult.Failed(IllegalArgumentException("Session duration must be between 1 minute and 168 hours."))
        }

        // Clean up any expired session first, then read the winner of the race in one place.
        cleanupStaleSessionUseCase()
        val now = System.currentTimeMillis()
        val existing = sessionRepository.getActiveSessionSync()
        if (existing != null && existing.expiresAt > now) {
            Timber.w("ActivateSessionUseCase: Active session already exists, returning AlreadyActive")
            return ActivationResult.AlreadyActive(existing)
        }

        val expiresAt = now + durationMillis
        var sessionId: Long? = null
        return try {
            val activeCategories = blockingRepository.getActiveCategoryKeys()
            val blockedApps = appBlockSelectionStore.selected.value.toList()
            // Atomic: a concurrent activation (worker + user tap) can no longer insert a second
            // ACTIVE row between the check above and this write.
            val createdSessionId =
                sessionRepository.startSessionIfNoneActive(expiresAt, activeCategories, blockedApps)
            if (createdSessionId == null) {
                val winner = sessionRepository.getActiveSessionSync()
                return if (winner != null) ActivationResult.AlreadyActive(winner)
                else ActivationResult.Failed(IllegalStateException("Activation was rejected without a live session."))
            }
            sessionId = createdSessionId
            workScheduler.scheduleSessionTimers(expiresAt)
            sessionRuntimeController.startRuntime(durationMillis)
            // Clear only after everything succeeded so a scheduler/runtime failure preserves selection for retry
            appBlockSelectionStore.clear()
            Timber.i("ActivateSessionUseCase: Session successfully activated. SessionId: $createdSessionId, expiresAt: $expiresAt")
            ActivationResult.Activated(createdSessionId, expiresAt)
        } catch (e: CancellationException) {
            Timber.w(e, "ActivateSessionUseCase: Activation cancelled. Rolling back.")
            rollbackActivation(sessionId)
            throw e
        } catch (e: Exception) {
            Timber.e(e, "ActivateSessionUseCase: Error activating session. Rolling back.")
            rollbackActivation(sessionId)
            ActivationResult.Failed(e)
        }
    }

    private suspend fun rollbackActivation(sessionId: Long?) = withContext(NonCancellable) {
        runCatching { 
            workScheduler.cancelSessionTimers() 
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

    companion object {
        /** Shortest session the activator will start; callers skip windows with less left than this. */
        const val MIN_DURATION_MILLIS = 1 * 60 * 1000L
        const val MAX_DURATION_MILLIS = 168 * 60 * 60 * 1000L
    }
}
