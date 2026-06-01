package com.focuslock.worker

sealed interface SessionWorkerDecision {
    data object NoActiveSession : SessionWorkerDecision
    data object Expired : SessionWorkerDecision
    data class Active(val remainingMillis: Long) : SessionWorkerDecision

    companion object {
        fun evaluate(expiresAt: Long?, now: Long): SessionWorkerDecision {
            if (expiresAt == null) return NoActiveSession

            val remainingMillis = expiresAt - now
            return if (remainingMillis <= 0) Expired else Active(remainingMillis)
        }
    }
}
