package com.focuslock.domain.model

data class UnlockRequest(
    val id: Long,
    val sessionId: Long,
    val requestedAt: Long,
    val status: UnlockRequestStatus,
    val otpAttempts: Int,
    val cooldownEndsAt: Long?
) {
    val maxAttemptsReached: Boolean get() = otpAttempts >= 3
    val cooldownRemainingMillis: Long get() =
        ((cooldownEndsAt ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
}

enum class UnlockRequestStatus {
    PENDING, OTP_SENT, OTP_VERIFIED, COOLDOWN, COMPLETED, FAILED, EMERGENCY
}
