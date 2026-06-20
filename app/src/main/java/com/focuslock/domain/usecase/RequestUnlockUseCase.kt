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

        // Step 1: Create the local DB record to track this request.
        // If this fails, there is nothing to clean up — return immediately.
        val requestId: Long = try {
            unlockRepository.createRequest(sessionId)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to record unlock request in local database")
            return@withContext Result.failure(
                Exception("Failed to record unlock request in local database: ${e.message}", e)
            )
        }

        // Step 2: Call the backend to generate and send the OTP email.
        try {
            val result = unlockRepository.requestUnlock(
                trustedContactEmail = trustedContactEmail,
                blockedCategories = blockedCategories,
                blockedKeywords = blockedKeywords,
                timeRemaining = timeRemaining
            )

            if (result.isSuccess && result.getOrNull() == true) {
                // Step 3a: Backend confirmed email was sent — mark as OTP_SENT.
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.OTP_SENT)
                Result.success(requestId)
            } else {
                // Step 3b: Backend responded but reported failure (e.g., Brevo error).
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED)
                Result.failure(
                    result.exceptionOrNull() ?: Exception("Backend failed to send OTP email.")
                )
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Network exception during request unlock")
            // Step 3c: Network exception (connection refused, timeout, etc.).
            // requestId is guaranteed non-null here — safe to update.
            try {
                unlockRepository.updateStatus(requestId, UnlockRequestStatus.FAILED)
            } catch (cleanupError: Exception) {
                timber.log.Timber.e(cleanupError, "Error updating status during cleanup")
                // Log only — original exception is what the caller needs.
            }
            Result.failure(Exception("Network error sending OTP: ${e.message}", e))
        }
    }
}
