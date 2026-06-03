package com.focuslock.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslock.data.local.entity.UnlockRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnlockRequestDao {
    
    @Query("SELECT * FROM unlock_requests WHERE session_id = :sessionId ORDER BY requested_at DESC")
    fun getRequestsBySession(sessionId: Long): Flow<List<UnlockRequestEntity>>
    
    @Query("SELECT * FROM unlock_requests WHERE session_id = :sessionId ORDER BY requested_at DESC LIMIT 1")
    fun getLatestRequestBySession(sessionId: Long): Flow<UnlockRequestEntity?>
    
    @Query("SELECT * FROM unlock_requests WHERE session_id = :sessionId ORDER BY requested_at DESC LIMIT 1")
    suspend fun getLatestRequestBySessionSync(sessionId: Long): UnlockRequestEntity?
    
    @Query("SELECT * FROM unlock_requests WHERE id = :requestId")
    fun getRequestById(requestId: Long): Flow<UnlockRequestEntity?>
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRequest(request: UnlockRequestEntity): Long
    
    @Update
    suspend fun updateRequest(request: UnlockRequestEntity)
    
    @Query("UPDATE unlock_requests SET status = :status WHERE id = :requestId")
    suspend fun updateRequestStatus(requestId: Long, status: String)
    
    @Query("UPDATE unlock_requests SET otp_attempts = otp_attempts + 1 WHERE id = :requestId")
    suspend fun incrementOtpAttempts(requestId: Long)

    @Query("SELECT otp_attempts FROM unlock_requests WHERE id = :requestId")
    suspend fun getOtpAttempts(requestId: Long): Int
    
    @Query("UPDATE unlock_requests SET cooldown_ends_at = :endsAt WHERE id = :requestId")
    suspend fun updateCooldownEndsAt(requestId: Long, endsAt: Long)
    
    @Query("DELETE FROM unlock_requests WHERE session_id = :sessionId")
    suspend fun deleteRequestsBySession(sessionId: Long)
}
