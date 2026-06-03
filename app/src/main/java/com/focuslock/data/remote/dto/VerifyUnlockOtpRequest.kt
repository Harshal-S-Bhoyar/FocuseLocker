package com.focuslock.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VerifyUnlockOtpRequest(
    val trustedContactEmail: String,
    val otp: String
)

@Serializable
data class VerifyUnlockOtpResponse(
    val valid: Boolean
)
