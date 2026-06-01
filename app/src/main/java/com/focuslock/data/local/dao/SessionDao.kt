package com.focuslock.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslock.data.local.entity.SessionEntity
import com.focuslock.data.local.entity.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    
    @Query("SELECT * FROM sessions WHERE status = :status LIMIT 1")
    fun getSessionByStatus(status: String = SessionStatus.ACTIVE): Flow<SessionEntity?>
    
    @Query("SELECT * FROM sessions WHERE status = :status AND expires_at > :currentTime LIMIT 1")
    suspend fun getActiveSessionSync(
        status: String = SessionStatus.ACTIVE,
        currentTime: Long = System.currentTimeMillis()
    ): SessionEntity?
    
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: Long): Flow<SessionEntity?>
    
    @Query("SELECT * FROM sessions ORDER BY created_at DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: SessionEntity): Long
    
    @Update
    suspend fun updateSession(session: SessionEntity)
    
    @Query("UPDATE sessions SET status = :status WHERE id = :sessionId")
    suspend fun updateSessionStatus(sessionId: Long, status: String)
    
    @Query("UPDATE sessions SET unlock_requested_at = :timestamp WHERE id = :sessionId")
    suspend fun updateUnlockRequestedAt(sessionId: Long, timestamp: Long)
    
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
    
    @Query("SELECT COUNT(*) FROM sessions WHERE status = :status")
    suspend fun countSessionsByStatus(status: String = SessionStatus.ACTIVE): Int
}
