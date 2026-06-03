package com.focuslock.domain.usecase

import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.UnlockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RequestUnlockUseCase @Inject constructor(
    private val unlockRepository: UnlockRepository
) {
    suspend operator fun invoke(
        sessionId: Long,
        trustedContactEmail: String,
        blockedCategories: List<String>,
        blockedKeywords: List<String>,
        timeRemaining: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        var requestId: Long? = null
        try {
            requestId = unlockRepository.createRequest(sessionId)
            
            val result = unlockRepository.requestUnlock(
                trustedContactEmail = trustedContactEmail,
                blockedCategories = blockedCategories,
                blockedKeywords = blockedKeywords,
                timeRemaining = timeRemaining
            )
            
            if (result.isSuccess && result.getOrNull() == true) {
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.OTP_SENT)
                Result.success(requestId)
            } else {
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED)
                Result.failure(result.exceptionOrNull() ?: Exception("Backend request failed"))
            }
        } catch (e: Exception) {
            requestId?.let {
                unlockRepository.updateStatus(it, UnlockRequestStatus.FAILED)
            }
            Result.failure(Exception("An unexpected error occurred.", e))
        }
    }
}
