package com.focuslock.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConfirmContactRequest(
    val email: String,
    val otp: String
)

@Serializable
data class ConfirmContactResponse(
    val verified: Boolean
)
