package com.focuslock.domain.usecase

import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.remote.dto.VerifyUnlockOtpRequest
import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.UnlockRepository
import com.focuslock.domain.worker.UnlockWorkerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class VerifyUnlockResult {
    object Valid : VerifyUnlockResult()
    object Invalid : VerifyUnlockResult()
    object MaxAttemptsReached : VerifyUnlockResult()
    data class Error(val message: String) : VerifyUnlockResult()
}

class VerifyUnlockOtpUseCase @Inject constructor(
    private val unlockRepository: UnlockRepository,
    private val api: FocusLockApi,
    private val unlockWorkerScheduler: UnlockWorkerScheduler
) {
    suspend operator fun invoke(
        requestId: Long,
        trustedContactNumber: String,
        otp: String
    ): VerifyUnlockResult = withContext(Dispatchers.IO) {
        if (otp.length != 6 || !otp.all { it.isDigit() }) {
            return@withContext VerifyUnlockResult.Invalid
        }

        try {
            val attempts = unlockRepository.incrementOtpAttempts(requestId)
            if (attempts > 3) {
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED)
                return@withContext VerifyUnlockResult.MaxAttemptsReached
            }

            val request = VerifyUnlockOtpRequest(
                trustedContactNumber = trustedContactNumber,
                otp = otp
            )
            val response = api.verifyUnlockOtp(request)

            if (response.valid) {
                val endsAt = System.currentTimeMillis() + 10 * 60 * 1000L
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.COOLDOWN)
                unlockRepository.setCooldownEndsAt(requestId, endsAt)
                unlockWorkerScheduler.scheduleCooldownTimer(endsAt)
                VerifyUnlockResult.Valid
            } else {
                if (attempts >= 3) {
                    unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED)
                    VerifyUnlockResult.MaxAttemptsReached
                } else {
                    VerifyUnlockResult.Invalid
                }
            }
        } catch (e: Exception) {
            VerifyUnlockResult.Error("Network error. Please try again.")
        }
    }
}
