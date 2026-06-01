package com.focuslock.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.focuslock.data.local.entity.TrustedContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedContactDao {
    
    @Query("SELECT * FROM trusted_contacts WHERE is_verified = 1 LIMIT 1")
    fun getVerifiedContact(): Flow<TrustedContactEntity?>
    
    @Query("SELECT * FROM trusted_contacts LIMIT 1")
    suspend fun getContactSync(): TrustedContactEntity?
    
    @Query("SELECT * FROM trusted_contacts LIMIT 1")
    fun getContact(): Flow<TrustedContactEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: TrustedContactEntity): Long
    
    @Update
    suspend fun updateContact(contact: TrustedContactEntity)
    
    @Query("UPDATE trusted_contacts SET is_verified = :isVerified, verified_at = :verifiedAt WHERE id = :contactId")
    suspend fun updateVerificationStatus(contactId: Long, isVerified: Boolean, verifiedAt: Long?)
    
    @Query("DELETE FROM trusted_contacts")
    suspend fun deleteAllContacts()
    
    @Query("SELECT COUNT(*) FROM trusted_contacts")
    suspend fun getContactCount(): Int
}
