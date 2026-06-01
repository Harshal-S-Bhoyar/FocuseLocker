package com.focuslock.domain.usecase

import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.remote.dto.VerifyContactRequest
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.repository.TrustedContactRepository
import javax.inject.Inject

sealed class SaveContactResult {
    object OtpSent : SaveContactResult()
    data class Error(val message: String) : SaveContactResult()
}

/**
 * Saves a phone number and triggers OTP send to that number.
 * Only allowed when no session is ACTIVE (US-02 acceptance criteria).
 */
class SaveTrustedContactUseCase @Inject constructor(
    private val trustedContactRepository: TrustedContactRepository,
    private val sessionRepository: SessionRepository,
    private val api: FocusLockApi
) {
    suspend operator fun invoke(phoneNumber: String): SaveContactResult {
        val trimmed = phoneNumber.trim()

        if (trimmed.isBlank()) {
            return SaveContactResult.Error("Phone number cannot be empty")
        }

        // Block update during active session — US-02 acceptance criteria
        if (sessionRepository.hasActiveSession()) {
            return SaveContactResult.Error("Cannot change trusted contact during an active session")
        }

        return try {
            // Save locally first (unverified)
            trustedContactRepository.saveContact(trimmed)

            // Send OTP via backend
            api.verifyContact(VerifyContactRequest(phoneNumber = trimmed))

            SaveContactResult.OtpSent
        } catch (e: Exception) {
            SaveContactResult.Error("Failed to send OTP. Check your internet connection and try again.")
        }
    }
}
