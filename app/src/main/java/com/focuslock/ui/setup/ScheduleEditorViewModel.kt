package com.focuslock.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.ActivationResult
import com.focuslock.domain.usecase.ReconcileScheduledSessionUseCase
import com.focuslock.domain.usecase.ScheduleEngine
import com.focuslock.domain.usecase.ScheduleValidation
import com.focuslock.data.repository.AppSettingsRepository
import com.focuslock.data.worker.WorkScheduler
import com.focuslock.domain.usecase.HasVerifiedContactUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ScheduleEditorViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleEngine: ScheduleEngine,
    private val hasVerifiedContactUseCase: HasVerifiedContactUseCase,
    private val appSettingsRepository: AppSettingsRepository,
    private val activateSessionUseCase: ActivateSessionUseCase,
    private val workScheduler: WorkScheduler,
    private val sessionRepository: SessionRepository,
    private val reconcileScheduledSessionUseCase: ReconcileScheduledSessionUseCase
) : ViewModel() {

    val windows: StateFlow<List<ScheduleWindowEntity>> =
        scheduleRepository.getWindows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleEnabled: StateFlow<Boolean> =
        scheduleRepository.isScheduleEnabled().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _hasVerifiedContact = MutableStateFlow(false)
    val hasVerifiedContact: StateFlow<Boolean> = _hasVerifiedContact.asStateFlow()

    private val _isContactLoaded = MutableStateFlow(false)
    val isContactLoaded: StateFlow<Boolean> = _isContactLoaded.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Non-error feedback for edits that meet a live session (strict lock-in explains itself
    // instead of looking broken).
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    // Serializes arming: a double tap must not run two arming flows, whose second outcome
    // could overwrite the first one's hand-off while navigation is still settling.
    private val armInFlight = Mutex()

    init {
        viewModelScope.launch {
            // A failed read must still finish loading, or Next stays disabled with no error.
            _hasVerifiedContact.value = runCatching { hasVerifiedContactUseCase() }.getOrDefault(false)
            _isContactLoaded.value = true
        }
    }

    suspend fun armScheduleAndCheckActivation(now: LocalDateTime = LocalDateTime.now()): ScheduleArmOutcome {
        if (!armInFlight.tryLock()) {
            Timber.d("ScheduleEditor: arming already in flight, ignoring duplicate tap")
            return ScheduleArmOutcome.ArmInFlight
        }
        try {
            return armSchedule(now)
        } finally {
            armInFlight.unlock()
        }
    }

    private suspend fun armSchedule(now: LocalDateTime): ScheduleArmOutcome {
        // ponytail: Arming schedule enables it, schedules background checks, completes onboarding, and starts immediate session if due
        _error.value = null
        // Read before mutating: a storage failure must fail before anything is armed,
        // so a failed arming never leaves half-applied state behind.
        val currentWindows = runCatching { scheduleRepository.getWindowsSync() }
            .getOrElse {
                Timber.e(it, "ScheduleEditor: reading windows failed")
                _error.value = "Could not read the saved schedule. Please try again."
                return ScheduleArmOutcome.Failed
            }
        // Stored rows can go stale (concurrent edit, restored backup). Arming must not enable
        // a schedule the engine would refuse to save.
        if (scheduleEngine.validate(currentWindows) != ScheduleValidation.Valid) {
            _error.value = "The saved schedule is invalid. Please review the windows."
            return ScheduleArmOutcome.Failed
        }
        val wasEnabled = runCatching { scheduleRepository.isScheduleEnabled().first() }
            .getOrElse { false }
        // A failed enable stops the flow: onboarding must not complete for a schedule that was never enabled.
        runCatching { scheduleRepository.setScheduleEnabled(true) }
            .onFailure {
                Timber.e(it, "ScheduleEditor: setScheduleEnabled failed")
                _error.value = "Could not enable the schedule. Please try again."
                return ScheduleArmOutcome.Failed
            }

        // Without the periodic worker the schedule can never fire, so this is a failure of arming
        // rather than a detail to log: reporting success here would leave a schedule that reads
        // as armed and silently never runs. Restore the prior flag so retry starts clean.
        runCatching { workScheduler.scheduleScheduleChecks() }
            .onFailure {
                Timber.e(it, "ScheduleEditor: scheduleScheduleChecks failed")
                _error.value = "Could not schedule the background checks. Please try again."
                runCatching { scheduleRepository.setScheduleEnabled(wasEnabled) }
                return ScheduleArmOutcome.Failed
            }

        // The schedule is armed and its checks are enqueued by now. A failure to flip the
        // onboarding flag keeps setup retryable instead of advancing to the terminal armed
        // state: the armed schedule is preserved and Done stays available for retry.
        runCatching { appSettingsRepository.setOnboardingComplete() }
            .onFailure {
                Timber.e(it, "ScheduleEditor: setOnboardingComplete failed")
                _error.value = "Schedule armed, but setup could not be finalised. Please try again."
                return ScheduleArmOutcome.Failed
            }

        val weekdayIso = now.dayOfWeek.value
        val minutesNow = now.hour * 60 + now.minute
        val due = scheduleEngine.dueWindow(currentWindows, weekdayIso, minutesNow)
            ?: return ScheduleArmOutcome.ArmedIdle
        // Exact boundary millis, not rounded minutes: a start at 22:53:46 for a window ending at
        // midnight must expire at 00:00:00, not 46 seconds past it.
        val remainingMillis = scheduleEngine.remainingMillis(due, now)
        if (remainingMillis < ActivateSessionUseCase.MIN_DURATION_MILLIS) return ScheduleArmOutcome.ArmedIdle
        return when (val result = activateSessionUseCase(remainingMillis)) {
            is ActivationResult.Activated -> {
                Timber.i("ScheduleEditor: active window started immediately for $remainingMillis ms")
                // Record the serviced occurrence exactly like the periodic worker does, so the
                // worker does not restart it after a legitimate early end.
                runCatching { scheduleRepository.setLastFiredSchedule(scheduleEngine.occurrenceKey(due, weekdayIso, now.toLocalDate())) }
                    .onFailure { Timber.e(it, "ScheduleEditor: setLastFiredSchedule failed") }
                // Hand the live window to the session confirmation step.
                ScheduleArmOutcome.LiveWindow(remainingMillis)
            }
            is ActivationResult.AlreadyActive -> {
                // Strict lock-in: a live session is a hand-off, never an error. Extend it only
                // if the due window now outlasts it; otherwise leave it untouched.
                val expiresAt = when (val reconciled = reconcileScheduledSessionUseCase(now)) {
                    is ReconcileScheduledSessionUseCase.Result.Extended -> reconciled.expiresAt
                    is ReconcileScheduledSessionUseCase.Result.Unchanged -> reconciled.expiresAt
                    is ReconcileScheduledSessionUseCase.Result.NothingActive -> result.session.expiresAt
                }
                val handoffMillis = expiresAt - scheduleEngine.epochMillis(now)
                if (handoffMillis < 1L) {
                    ScheduleArmOutcome.ArmedIdle
                } else {
                    Timber.i("ScheduleEditor: hand-off to the already running session")
                    ScheduleArmOutcome.LiveWindow(handoffMillis)
                }
            }
            is ActivationResult.Failed -> {
                // The schedule is armed and its checks are enqueued, but the immediate start
                // failed: report it distinctly so the confirmation screen keeps a retry
                // available instead of parking on the terminal armed state.
                Timber.w(result.error, "ScheduleEditor: immediate activation failed")
                _error.value = "Schedule armed, but the session could not start now. Please try again."
                ScheduleArmOutcome.ActivationFailed
            }
        }
    }

    fun saveWindow(
        id: Long?,
        weekday: Int,
        startMinutes: Int,
        endMinutes: Int,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        val candidate = ScheduleWindowEntity(
            id = id ?: 0L,
            weekday = weekday,
            startMinutes = startMinutes,
            endMinutes = endMinutes
        )
        val current = windows.value.filter { it.id != candidate.id || candidate.id == 0L }
        when (val result = scheduleEngine.validate(current + candidate)) {
            is ScheduleValidation.Valid -> {
                _error.value = null
                _notice.value = null
                // Compare the due window before the edit (full list) with after it, so re-shaping
                // the currently running window is recognised.
                val dueBefore = dueWindow(windows.value, now)
                val dueAfter = dueWindow(current + candidate, now)
                viewModelScope.launch {
                    runCatching { scheduleRepository.upsertWindow(candidate) }
                        .onFailure {
                            Timber.e(it, "ScheduleEditor: save failed")
                            // The optimistic true above cannot be retracted, so the failed write
                            // must at least surface instead of logging only.
                            _error.value = "Could not save the window. Please try again."
                        }
                        .onSuccess { reconcileAfterEdit(now, candidate.id, dueBefore, dueAfter) }
                }
                return true
            }
            is ScheduleValidation.Overlap -> {
                _error.value = "Overlapping with ${formatWindow(result.first)}"
                return false
            }
            is ScheduleValidation.Invalid -> {
                _error.value = result.reason
                return false
            }
        }
    }

    fun deleteWindow(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        _error.value = null
        _notice.value = null
        val current = windows.value
        val dueBefore = dueWindow(current, now)
        val dueAfter = dueWindow(current.filter { it.id != id }, now)
        viewModelScope.launch {
            runCatching { scheduleRepository.deleteWindow(id) }
                .onFailure {
                    Timber.e(it, "ScheduleEditor: delete failed")
                    _error.value = "Could not delete the window. Please try again."
                }
                .onSuccess { reconcileAfterEdit(now, id, dueBefore, dueAfter) }
        }
    }

    fun setScheduleEnabled(enabled: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        _notice.value = null
        viewModelScope.launch {
            runCatching { scheduleRepository.setScheduleEnabled(enabled) }
                .onFailure { Timber.e(it, "ScheduleEditor: switch failed") }
            // Strict lock-in: pausing the schedule pauses future windows only. Say so, or a
            // still-running session looks like a bug.
            if (!enabled) {
                val active = runCatching { sessionRepository.getActiveSessionSync() }.getOrNull() ?: return@launch
                if (active.expiresAt <= scheduleEngine.epochMillis(now)) return@launch
                _notice.value = "Schedule paused for upcoming sessions. Your current active session will finish at ${formatEpochTime(active.expiresAt)}."
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun formatWindow(window: ScheduleWindowEntity): String {
        val day = DayOfWeek.of(window.weekday).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return "$day ${formatMinutes(window.startMinutes)} to ${formatMinutes(window.endMinutes)}"
    }

    fun formatMinutes(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun dueWindow(windows: List<ScheduleWindowEntity>, now: LocalDateTime): ScheduleWindowEntity? =
        scheduleEngine.dueWindow(windows, now.dayOfWeek.value, now.hour * 60 + now.minute)

    /**
     * Strict lock-in after a schedule edit: extend a live session only when the edit touches the
     * currently due window and that window now ends later. A shortened, removed, or unrelated
     * window never shortens or cancels the session — only [notice] explains it.
     */
    private suspend fun reconcileAfterEdit(
        now: LocalDateTime,
        editedId: Long,
        dueBefore: ScheduleWindowEntity?,
        dueAfter: ScheduleWindowEntity?
    ) {
        if (dueBefore?.id != editedId && dueAfter?.id != editedId) return
        val endBefore = dueBefore?.let { scheduleEngine.endEpochMillis(it, now) }
        val endAfter = dueAfter?.let { scheduleEngine.endEpochMillis(it, now) }
        when (val reconciled = reconcileScheduledSessionUseCase(now)) {
            is ReconcileScheduledSessionUseCase.Result.Extended ->
                _notice.value = "Schedule updated. Your current session now runs until ${formatEpochTime(reconciled.expiresAt)}."
            is ReconcileScheduledSessionUseCase.Result.Unchanged ->
                if (endBefore != null && (endAfter == null || endAfter < endBefore)) {
                    _notice.value = "Schedule updated for upcoming sessions. Your current active session will finish at ${formatEpochTime(reconciled.expiresAt)}."
                }
            is ReconcileScheduledSessionUseCase.Result.NothingActive -> Unit
        }
    }

    private fun formatEpochTime(epochMillis: Long): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        return formatMinutes(time.hour * 60 + time.minute)
    }
}

/**
 * What arming the weekly schedule produced. A typed outcome replaces the previous boolean, which
 * conflated "armed, nothing due now" with "arming failed" — the screen rendered both as the armed
 * confirmation, so a failure showed the success banner with the retry button disabled.
 */
sealed interface ScheduleArmOutcome {
    /** Armed, and no window covers the current time: the terminal confirmation state. */
    data object ArmedIdle : ScheduleArmOutcome

    /** Armed, and a window covers the current time; [remainingMillis] is what is left of it. */
    data class LiveWindow(val remainingMillis: Long) : ScheduleArmOutcome

    /** Nothing was armed. [ScheduleEditorViewModel.error] says why, and arming may be retried. */
    data object Failed : ScheduleArmOutcome

    /**
     * Armed and its checks are enqueued, but the immediate start failed for a real reason.
     * Like [Failed] it keeps Done available for retry; unlike it, the schedule
     * itself is in place, so retry resumes from arming rather than setup.
     * A live session never maps here — that is a [LiveWindow] hand-off.
     */
    data object ActivationFailed : ScheduleArmOutcome

    /** Another arming call is already in flight; this duplicate tap changed nothing. */
    data object ArmInFlight : ScheduleArmOutcome
}
