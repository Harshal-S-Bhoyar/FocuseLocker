package com.focuslock.data.repository

import com.focuslock.data.local.dao.TrustedContactDao
import com.focuslock.data.local.entity.TrustedContactEntity
import com.focuslock.domain.model.TrustedContact
import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.remote.dto.ConfirmContactRequest
import com.focuslock.data.remote.dto.VerifyContactRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustedContactRepository @Inject constructor(
    private val trustedContactDao: TrustedContactDao,
    private val api: FocusLockApi
) {

    fun getContact(): Flow<TrustedContact?> {
        return trustedContactDao.getContact().map { it?.toDomain() }
    }

    fun getVerifiedContact(): Flow<TrustedContact?> {
        return trustedContactDao.getVerifiedContact().map { it?.toDomain() }
    }

    suspend fun getContactSync(): TrustedContact? {
        return trustedContactDao.getContactSync()?.toDomain()
    }

    suspend fun saveContact(email: String): Long {
        // Enforce max 1 trusted contact for MVP
        trustedContactDao.deleteAllContacts()
        val entity = TrustedContactEntity(
            email = email,
            isVerified = false,
            verifiedAt = null,
            createdAt = System.currentTimeMillis()
        )
        return trustedContactDao.insertContact(entity)
    }

    suspend fun markVerified(contactId: Long) {
        trustedContactDao.updateVerificationStatus(
            contactId = contactId,
            isVerified = true,
            verifiedAt = System.currentTimeMillis()
        )
    }

    suspend fun hasVerifiedContact(): Boolean {
        return trustedContactDao.getContactSync()?.isVerified == true
    }

    suspend fun sendVerificationOtp(email: String): Boolean {
        // Actually read the response — backend returns {success: true/false}
        // Previously this always returned true even when the email failed to send
        val response = api.verifyContact(VerifyContactRequest(email = email))
        return response.success
    }

    suspend fun confirmOtp(email: String, otp: String): Boolean {
        return try {
            // Backend returns 200 with {verified: true} on success
            val response = api.confirmContact(ConfirmContactRequest(email = email, otp = otp))
            response.verified
        } catch (e: retrofit2.HttpException) {
            // Backend returns 400 with {verified: false} on wrong OTP
            false
        } catch (e: Exception) {
            // Network or other errors
            false
        }
    }
    private fun TrustedContactEntity.toDomain() = TrustedContact(
        id = id,
        email = email,
        isVerified = isVerified,
        verifiedAt = verifiedAt,
        createdAt = createdAt
    )
}
