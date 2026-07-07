package com.focuslock.domain.session

/**
 * Synchronous mirror of enforcement state for framework callbacks that cannot suspend.
 */
interface SessionEnforcementStateStore {
    fun isSessionActive(): Boolean
    fun setSessionActive(isActive: Boolean)
    fun getBreachCount(): Int
    fun incrementBreachCount()
    fun resetBreachCount()
    fun getPenaltyEndTime(): Long
    fun setPenaltyEndTime(timeMillis: Long)
}
