package com.focuslock.domain.repository

interface BackgroundSyncScheduler {
    fun schedulePeriodicSync()
}
