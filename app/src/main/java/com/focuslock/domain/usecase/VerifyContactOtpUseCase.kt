package com.focuslock.domain.usecase

import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.remote.dto.ConfirmContactRequest
import com.focuslock.domain.repository.TrustedContactRepository
import javax.inject.Inject

sealed class VerifyOtpResult {
    object Verified : VerifyOtpResult()
    object InvalidOtp : VerifyOtpResult()
    data class Error(val message: String) : VerifyOtpResult()
}

/**
 * Verifies the OTP entered by the user against the backend.
 * On success, marks the contact as verified in Room.
 */
class VerifyContactOtpUseCase @Inject constructor(
    private val trustedContactRepository: TrustedContactRepository,
    private val api: FocusLockApi
) {
    suspend operator fun invoke(phoneNumber: String, otp: String): VerifyOtpResult {
        if (otp.length != 6 || !otp.all { it.isDigit() }) {
            return VerifyOtpResult.InvalidOtp
        }

        return try {
            val response = api.confirmContact(
                ConfirmContactRequest(phoneNumber = phoneNumber, otp = otp)
            )

            if (response.verified) {
                val contact = trustedContactRepository.getContactSync()
                contact?.let { trustedContactRepository.markVerified(it.id) }
                VerifyOtpResult.Verified
            } else {
                VerifyOtpResult.InvalidOtp
            }
        } catch (e: Exception) {
            VerifyOtpResult.Error("Verification failed. Check your connection and try again.")
        }
    }
}
