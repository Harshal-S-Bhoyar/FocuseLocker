package com.focuslock.domain.repository

import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun getActiveSession(): Flow<Session?>
    suspend fun getActiveSessionSync(): Session?
    suspend fun startSession(expiresAt: Long): Long
    suspend fun endSession(sessionId: Long, status: SessionStatus)
    suspend fun markUnlockRequested(sessionId: Long, timestamp: Long)
    suspend fun hasActiveSession(): Boolean
}
