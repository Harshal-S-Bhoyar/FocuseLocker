package com.focuslock.domain.usecase

import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.repository.TrustedContactRepository
import timber.log.Timber
import javax.inject.Inject

sealed class SaveContactResult {
    object OtpSent : SaveContactResult()
    data class Error(val message: String) : SaveContactResult()
}

/**
 * Saves an email address and triggers OTP send to that address.
 * Only allowed when no session is ACTIVE (US-02 acceptance criteria).
 */
class SaveTrustedContactUseCase @Inject constructor(
    private val trustedContactRepository: TrustedContactRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(email: String): SaveContactResult {
        val trimmed = email.trim()

        if (trimmed.isBlank()) {
            return SaveContactResult.Error("Email cannot be empty")
        }

        // Block update during active session — US-02 acceptance criteria
        if (sessionRepository.hasActiveSession()) {
            return SaveContactResult.Error("Cannot change trusted contact during an active session")
        }

        return try {
            // Save locally first (unverified)
            trustedContactRepository.saveContact(trimmed)

            // Send OTP via backend
            val sent = trustedContactRepository.sendVerificationOtp(trimmed)
            if (sent) SaveContactResult.OtpSent
            else SaveContactResult.Error("Failed to send verification email.")
        } catch (e: java.io.IOException) {
            Timber.e(e, "IOException during API call to verifyContact")
            SaveContactResult.Error("Failed to connect to the server. Check your internet connection. (${e.message})")
        } catch (e: retrofit2.HttpException) {
            Timber.e(e, "HttpException during API call to verifyContact")
            SaveContactResult.Error("Server error (${e.code()}). Failed to send OTP.")
        } catch (e: Exception) {
            Timber.e(e, "Unexpected Exception during API call to verifyContact")
            SaveContactResult.Error("An unexpected error occurred.")
        }
    }
}
