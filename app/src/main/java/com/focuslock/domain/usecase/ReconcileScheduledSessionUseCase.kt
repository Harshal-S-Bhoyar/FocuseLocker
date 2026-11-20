package com.focuslock.domain.usecase

import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.worker.WorkScheduler
import java.time.LocalDateTime
import javax.inject.Inject
import timber.log.Timber

/**
 * Strict lock-in for a running session against the weekly schedule.
 *
 * An active session may only be pushed LATER when the due window now ends after it, so an
 * extended window keeps enforcing. A shortened or removed window, or a paused schedule, never
 * shortens or cancels the session: otherwise moving an end time back one minute would be a
 * one-step bypass of an active block.
 */
class ReconcileScheduledSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val scheduleRepository: ScheduleRepository,
    private val scheduleEngine: ScheduleEngine,
    private val workScheduler: WorkScheduler
) {
    sealed interface Result {
        /** No live session: nothing to reconcile. */
        data object NothingActive : Result
        /** The live session already covers the due window; [expiresAt] is unchanged. */
        data class Unchanged(val expiresAt: Long) : Result
        /** The due window ends later; the session now expires at [expiresAt]. */
        data class Extended(val expiresAt: Long) : Result
    }

    suspend operator fun invoke(now: LocalDateTime = LocalDateTime.now()): Result {
        val session = sessionRepository.getActiveSessionSync() ?: return Result.NothingActive
        if (session.expiresAt <= scheduleEngine.epochMillis(now)) return Result.NothingActive

        val windows = scheduleRepository.getWindowsSync()
        val due = scheduleEngine.dueWindow(windows, now.dayOfWeek.value, now.hour * 60 + now.minute)
            ?: return Result.Unchanged(session.expiresAt)
        val remaining = scheduleEngine.remainingMillis(due, now)
        if (remaining < ActivateSessionUseCase.MIN_DURATION_MILLIS) return Result.Unchanged(session.expiresAt)

        val windowEnd = scheduleEngine.epochMillis(now) + remaining
        if (windowEnd <= session.expiresAt) return Result.Unchanged(session.expiresAt)

        sessionRepository.extendSession(session.id, windowEnd)
        workScheduler.scheduleSessionTimers(windowEnd)
        Timber.i("ReconcileScheduledSessionUseCase: session ${session.id} extended to $windowEnd")
        return Result.Extended(windowEnd)
    }
}
