package com.focuslock.data.repository

import com.focuslock.data.local.dao.TrustedContactDao
import com.focuslock.data.local.entity.TrustedContactEntity
import com.focuslock.domain.model.TrustedContact
import com.focuslock.domain.repository.TrustedContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustedContactRepositoryImpl @Inject constructor(
    private val trustedContactDao: TrustedContactDao
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

    override suspend fun saveContact(phoneNumber: String): Long {
        // Enforce max 1 trusted contact for MVP
        trustedContactDao.deleteAllContacts()
        val entity = TrustedContactEntity(
            phoneNumber = phoneNumber,
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

    private fun TrustedContactEntity.toDomain() = TrustedContact(
        id = id,
        phoneNumber = phoneNumber,
        isVerified = isVerified,
        verifiedAt = verifiedAt,
        createdAt = createdAt
    )
}
