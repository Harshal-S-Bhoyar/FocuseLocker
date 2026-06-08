package com.focuslock.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focuslock.data.local.entity.BlockedDomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {
    
    @Query("""
        SELECT bd.* FROM blocked_domains bd
        INNER JOIN blocked_categories bc ON bd.category_key = bc.category_key
        WHERE bc.is_active = 1
    """)
    fun getActiveBlockedDomains(): Flow<List<BlockedDomainEntity>>
    
    @Query("""
        SELECT bd.domain FROM blocked_domains bd
        INNER JOIN blocked_categories bc ON bd.category_key = bc.category_key
        WHERE bc.is_active = 1
    """)
    suspend fun getActiveBlockedDomainList(): List<String>
    
    @Query("SELECT DISTINCT domain FROM blocked_domains")
    suspend fun getAllBlockedDomainList(): List<String>
    
    @Query("SELECT DISTINCT domain FROM blocked_domains")
    fun getAllBlockedDomainListFlow(): Flow<List<String>>
    
    @Query("SELECT * FROM blocked_domains WHERE category_key = :categoryKey")
    fun getDomainsByCategory(categoryKey: String): Flow<List<BlockedDomainEntity>>
    
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_domains WHERE domain = :domain)")
    suspend fun domainExists(domain: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDomain(domain: BlockedDomainEntity)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDomains(domains: List<BlockedDomainEntity>)
    
    @Query("DELETE FROM blocked_domains WHERE category_key = :categoryKey")
    suspend fun deleteDomainsByCategory(categoryKey: String)
    
    @Query("DELETE FROM blocked_domains")
    suspend fun deleteAllDomains()
    
    @Query("SELECT COUNT(*) FROM blocked_domains")
    suspend fun getDomainCount(): Int

    @Query("SELECT COUNT(*) FROM blocked_domains WHERE domain = :domain")
    suspend fun countByDomain(domain: String): Int

    @Query("SELECT * FROM blocked_domains WHERE is_auto_blocked = 1 ORDER BY auto_blocked_at DESC")
    fun getAutoBlockedDomains(): Flow<List<BlockedDomainEntity>>
}
