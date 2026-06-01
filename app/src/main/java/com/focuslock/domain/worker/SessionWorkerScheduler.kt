package com.focuslock.domain.worker

interface SessionWorkerScheduler {
    fun scheduleSessionTimers(expiresAt: Long)
    fun cancelSessionTimers()
}
