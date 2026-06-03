package com.focuslock.domain.usecase

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
    private val unlockWorkerScheduler: UnlockWorkerScheduler
) {
    suspend operator fun invoke(
        requestId: Long,
        trustedContactEmail: String,
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

            val result = unlockRepository.verifyOtp(trustedContactEmail, otp)

            if (result.isSuccess) {
                val isValid = result.getOrNull() == true
                if (isValid) {
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
            } else {
                val e = result.exceptionOrNull()
                if (e is java.io.IOException) {
                    VerifyUnlockResult.Error("Network error. Please try again.")
                } else if (e is retrofit2.HttpException) {
                    VerifyUnlockResult.Error("Server error (${e.code()}). Please try again.")
                } else {
                    VerifyUnlockResult.Error("An unexpected error occurred.")
                }
            }
        } catch (e: Exception) {
            VerifyUnlockResult.Error("An unexpected error occurred.")
        }
    }
}
