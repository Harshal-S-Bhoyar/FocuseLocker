package com.focuslock.domain.usecase

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
    private val trustedContactRepository: TrustedContactRepository
) {
    suspend operator fun invoke(email: String, otp: String): VerifyOtpResult {
        if (otp.length != 6 || !otp.all { it.isDigit() }) {
            return VerifyOtpResult.InvalidOtp
        }

        return try {
            val verified = trustedContactRepository.confirmOtp(email, otp)

            if (verified) {
                val contact = trustedContactRepository.getContactSync()
                contact?.let { trustedContactRepository.markVerified(it.id) }
                VerifyOtpResult.Verified
            } else {
                VerifyOtpResult.InvalidOtp
            }
        } catch (e: java.io.IOException) {
            VerifyOtpResult.Error("Network error. Check your connection and try again.")
        } catch (e: retrofit2.HttpException) {
            VerifyOtpResult.Error("Server error (${e.code()}). Verification failed.")
        } catch (e: Exception) {
            VerifyOtpResult.Error("An unexpected error occurred.")
        }
    }
}
