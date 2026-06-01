package com.focuslock.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslock.data.local.entity.BlockedKeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedKeywordDao {
    
    @Query("SELECT * FROM blocked_keywords WHERE is_active = 1 ORDER BY keyword ASC")
    fun getActiveKeywords(): Flow<List<BlockedKeywordEntity>>
    
    @Query("SELECT * FROM blocked_keywords ORDER BY keyword ASC")
    fun getAllKeywords(): Flow<List<BlockedKeywordEntity>>
    
    @Query("SELECT COUNT(*) FROM blocked_keywords")
    suspend fun getKeywordCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKeyword(keyword: BlockedKeywordEntity): Long
    
    @Update
    suspend fun updateKeyword(keyword: BlockedKeywordEntity)
    
    @Query("UPDATE blocked_keywords SET is_active = :isActive WHERE id = :keywordId")
    suspend fun updateKeywordStatus(keywordId: Long, isActive: Boolean)
    
    @Query("DELETE FROM blocked_keywords WHERE id = :keywordId")
    suspend fun deleteKeyword(keywordId: Long)
    
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_keywords WHERE keyword = :keyword)")
    suspend fun keywordExists(keyword: String): Boolean
}
