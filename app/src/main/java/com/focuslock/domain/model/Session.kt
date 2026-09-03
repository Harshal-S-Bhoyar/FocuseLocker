package com.focuslock.domain.model

data class Session(
    val id: Long,
    val status: SessionStatus,
    val startedAt: Long,
    val expiresAt: Long,
    val unlockedEarly: Boolean,
    val unlockRequestedAt: Long?,
    val createdAt: Long,
    val selectedCategoryKeys: List<String> = emptyList()
) {
    val isActive: Boolean get() = status == SessionStatus.ACTIVE
    val isExpired: Boolean get() = expiresAt <= System.currentTimeMillis()
    val remainingMillis: Long get() = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
}

enum class SessionStatus {
    ACTIVE, COMPLETED, CANCELLED
}
