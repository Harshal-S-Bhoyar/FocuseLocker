package com.focuslock.domain.worker

interface UnlockWorkerScheduler {
    fun scheduleCooldownTimer(endsAt: Long)
    fun scheduleEmergencyBypass(endsAt: Long)
    fun cancelUnlockTimers()
}
