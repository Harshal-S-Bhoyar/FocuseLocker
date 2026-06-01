package com.focuslock.domain.repository

import com.focuslock.domain.model.TrustedContact
import kotlinx.coroutines.flow.Flow

interface TrustedContactRepository {
    fun getContact(): Flow<TrustedContact?>
    fun getVerifiedContact(): Flow<TrustedContact?>
    suspend fun getContactSync(): TrustedContact?
    suspend fun saveContact(phoneNumber: String): Long
    suspend fun markVerified(contactId: Long)
    suspend fun hasVerifiedContact(): Boolean
}
