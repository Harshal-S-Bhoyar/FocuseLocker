package com.focuslock.receiver

sealed interface BootRecoveryDecision {
    data object NoSession : BootRecoveryDecision
    data object CompleteExpired : BootRecoveryDecision
    data class Recover(val remainingMillis: Long) : BootRecoveryDecision

    companion object {
        fun evaluate(expiresAt: Long?, now: Long): BootRecoveryDecision {
            if (expiresAt == null) return NoSession

            val remainingMillis = expiresAt - now
            return if (remainingMillis <= 0) CompleteExpired else Recover(remainingMillis)
        }
    }
}
