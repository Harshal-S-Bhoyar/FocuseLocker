package com.focuslock.data.repository

import com.focuslock.data.local.dao.TrustedContactDao
import com.focuslock.data.local.entity.TrustedContactEntity
import com.focuslock.domain.model.TrustedContact
import com.focuslock.domain.repository.TrustedContactRepository
import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.remote.dto.ConfirmContactRequest
import com.focuslock.data.remote.dto.VerifyContactRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustedContactRepositoryImpl @Inject constructor(
    private val trustedContactDao: TrustedContactDao,
    private val api: FocusLockApi
) : TrustedContactRepository {

    override fun getContact(): Flow<TrustedContact?> {
        return trustedContactDao.getContact().map { it?.toDomain() }
    }

    override fun getVerifiedContact(): Flow<TrustedContact?> {
        return trustedContactDao.getVerifiedContact().map { it?.toDomain() }
    }

    override suspend fun getContactSync(): TrustedContact? {
        return trustedContactDao.getContactSync()?.toDomain()
    }

    override suspend fun saveContact(email: String): Long {
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

    override suspend fun markVerified(contactId: Long) {
        trustedContactDao.updateVerificationStatus(
            contactId = contactId,
            isVerified = true,
            verifiedAt = System.currentTimeMillis()
        )
    }

    override suspend fun hasVerifiedContact(): Boolean {
        return trustedContactDao.getContactSync()?.isVerified == true
    }

    override suspend fun sendVerificationOtp(email: String): Boolean {
        // Actually read the response — backend returns {success: true/false}
        // Previously this always returned true even when the email failed to send
        val response = api.verifyContact(VerifyContactRequest(email = email))
        return response.success
    }

    override suspend fun confirmOtp(email: String, otp: String): Boolean {
        // Backend returns 400 with {verified: false} on wrong OTP — Retrofit throws HttpException
        // Backend returns 200 with {verified: true} on success
        val response = api.confirmContact(ConfirmContactRequest(email = email, otp = otp))
        return response.verified
    }

    private fun TrustedContactEntity.toDomain() = TrustedContact(
        id = id,
        email = email,
        isVerified = isVerified,
        verifiedAt = verifiedAt,
        createdAt = createdAt
    )
}
