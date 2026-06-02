package com.focuslock.data.repository

import com.focuslock.data.local.dao.UnlockRequestDao
import com.focuslock.data.local.entity.UnlockRequestEntity
import com.focuslock.data.local.entity.UnlockRequestStatus as EntityStatus
import com.focuslock.domain.model.UnlockRequest
import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.UnlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.remote.dto.VerifyUnlockOtpRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnlockRepositoryImpl @Inject constructor(
    private val unlockRequestDao: UnlockRequestDao,
    private val api: FocusLockApi
) : UnlockRepository {

    override fun getLatestRequest(sessionId: Long): Flow<UnlockRequest?> {
        return unlockRequestDao.getLatestRequestBySession(sessionId)
            .map { it?.toDomain() }
    }

    override suspend fun createRequest(sessionId: Long): Long {
        val entity = UnlockRequestEntity(
            sessionId = sessionId,
            requestedAt = System.currentTimeMillis(),
            status = EntityStatus.PENDING,
            otpAttempts = 0,
            cooldownEndsAt = null
        )
        return unlockRequestDao.insertRequest(entity)
    }

    override suspend fun updateStatus(requestId: Long, status: UnlockRequestStatus) {
        unlockRequestDao.updateRequestStatus(requestId, status.toEntityStatus())
    }

    override suspend fun incrementOtpAttempts(requestId: Long): Int {
        val current = unlockRequestDao.getLatestRequestBySessionSync(requestId)
        val newCount = (current?.otpAttempts ?: 0) + 1
        unlockRequestDao.updateOtpAttempts(requestId, newCount)
        return newCount
    }

    override suspend fun setCooldownEndsAt(requestId: Long, endsAt: Long) {
        unlockRequestDao.updateCooldownEndsAt(requestId, endsAt)
    }

    override suspend fun getLatestRequestSync(sessionId: Long): UnlockRequest? {
        return unlockRequestDao.getLatestRequestBySessionSync(sessionId)?.toDomain()
    }

    private fun UnlockRequestEntity.toDomain() = UnlockRequest(
        id = id,
        sessionId = sessionId,
        requestedAt = requestedAt,
        status = when (status) {
            EntityStatus.PENDING      -> UnlockRequestStatus.PENDING
            EntityStatus.OTP_SENT     -> UnlockRequestStatus.OTP_SENT
            EntityStatus.OTP_VERIFIED -> UnlockRequestStatus.OTP_VERIFIED
            EntityStatus.COOLDOWN     -> UnlockRequestStatus.COOLDOWN
            EntityStatus.COMPLETED    -> UnlockRequestStatus.COMPLETED
            EntityStatus.FAILED       -> UnlockRequestStatus.FAILED
            EntityStatus.EMERGENCY    -> UnlockRequestStatus.EMERGENCY
            else                      -> UnlockRequestStatus.PENDING
        },
        otpAttempts = otpAttempts,
        cooldownEndsAt = cooldownEndsAt
    )

    private fun UnlockRequestStatus.toEntityStatus(): String = when (this) {
        UnlockRequestStatus.PENDING      -> EntityStatus.PENDING
        UnlockRequestStatus.OTP_SENT     -> EntityStatus.OTP_SENT
        UnlockRequestStatus.OTP_VERIFIED -> EntityStatus.OTP_VERIFIED
        UnlockRequestStatus.COOLDOWN     -> EntityStatus.COOLDOWN
        UnlockRequestStatus.COMPLETED    -> EntityStatus.COMPLETED
        UnlockRequestStatus.FAILED       -> EntityStatus.FAILED
        UnlockRequestStatus.EMERGENCY    -> EntityStatus.EMERGENCY
    }

    override suspend fun verifyOtp(trustedContactNumber: String, otp: String): Boolean {
        val request = VerifyUnlockOtpRequest(
            trustedContactNumber = trustedContactNumber,
            otp = otp
        )
        return api.verifyUnlockOtp(request).valid
    }
}
