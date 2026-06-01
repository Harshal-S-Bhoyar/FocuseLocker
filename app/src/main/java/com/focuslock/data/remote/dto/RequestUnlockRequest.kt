package com.focuslock.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RequestUnlockRequest(
    val trustedContactNumber: String,
    val blockedCategories: List<String>,
    val blockedKeywords: List<String>,
    val timeRemaining: String,
    val requestTimestamp: Long
)

@Serializable
data class RequestUnlockResponse(
    val success: Boolean
)
