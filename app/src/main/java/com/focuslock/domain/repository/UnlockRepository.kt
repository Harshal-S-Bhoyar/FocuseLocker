package com.focuslock.domain.repository

import com.focuslock.domain.model.UnlockRequest
import com.focuslock.domain.model.UnlockRequestStatus
import kotlinx.coroutines.flow.Flow

interface UnlockRepository {
    fun getLatestRequest(sessionId: Long): Flow<UnlockRequest?>
    suspend fun createRequest(sessionId: Long): Long
    suspend fun updateStatus(requestId: Long, status: UnlockRequestStatus)
    suspend fun incrementOtpAttempts(requestId: Long): Int
    suspend fun setCooldownEndsAt(requestId: Long, endsAt: Long)
    suspend fun getLatestRequestSync(sessionId: Long): UnlockRequest?
}
