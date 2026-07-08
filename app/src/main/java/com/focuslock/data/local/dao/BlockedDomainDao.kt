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
        SELECT DISTINCT bd.domain 
        FROM blocked_domains bd
        INNER JOIN blocked_categories bc ON bd.category_key = bc.category_key
        WHERE bc.is_active = 1 
          AND (bd.block_status IS NULL OR bd.block_status = 'PERMANENT')
          AND (bd.expires_at IS NULL OR bd.expires_at > :now)
    """)
    suspend fun getActiveBlockedDomainList(now: Long = System.currentTimeMillis()): List<String>

    @Query("""
        SELECT DISTINCT bd.domain 
        FROM blocked_domains bd
        INNER JOIN blocked_categories bc ON bd.category_key = bc.category_key
        WHERE bc.is_active = 1 
          AND (bd.block_status IS NULL OR bd.block_status = 'PERMANENT')
          AND (bd.expires_at IS NULL OR bd.expires_at > :now)
    """)
    fun getActiveBlockedDomainListFlow(now: Long = System.currentTimeMillis()): Flow<List<String>>
    

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


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDomain(domain: BlockedDomainEntity)

    @Query("SELECT * FROM blocked_domains WHERE domain = :domain AND (expires_at IS NULL OR expires_at > :now)")
    suspend fun findActiveDomain(domain: String, now: Long = System.currentTimeMillis()): BlockedDomainEntity?

    @Query("DELETE FROM blocked_domains WHERE expires_at IS NOT NULL AND expires_at < :now")
    suspend fun deleteExpiredEntries(now: Long = System.currentTimeMillis())
}
