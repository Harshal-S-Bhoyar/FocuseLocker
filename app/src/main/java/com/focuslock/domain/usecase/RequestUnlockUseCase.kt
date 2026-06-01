package com.focuslock.domain.usecase

import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.remote.dto.RequestUnlockRequest
import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.domain.repository.UnlockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RequestUnlockUseCase @Inject constructor(
    private val unlockRepository: UnlockRepository,
    private val api: FocusLockApi
) {
    suspend operator fun invoke(
        sessionId: Long,
        trustedContactNumber: String,
        blockedCategories: List<String>,
        blockedKeywords: List<String>,
        timeRemaining: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        var requestId: Long? = null
        try {
            requestId = unlockRepository.createRequest(sessionId)
            
            val request = RequestUnlockRequest(
                trustedContactNumber = trustedContactNumber,
                blockedCategories = blockedCategories,
                blockedKeywords = blockedKeywords,
                timeRemaining = timeRemaining,
                requestTimestamp = System.currentTimeMillis()
            )
            
            val response = api.requestUnlock(request)
            
            if (response.success) {
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.OTP_SENT)
                Result.success(requestId)
            } else {
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED)
                Result.failure(Exception("Backend request failed"))
            }
        } catch (e: Exception) {
            requestId?.let {
                unlockRepository.updateStatus(it, UnlockRequestStatus.FAILED)
            }
            Result.failure(e)
        }
    }
}
