package com.focuslock.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VerifyContactRequest(
    val phoneNumber: String
)

@Serializable
data class VerifyContactResponse(
    val success: Boolean
)
