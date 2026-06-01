package com.focuslock.domain.model

data class TrustedContact(
    val id: Long,
    val phoneNumber: String,
    val isVerified: Boolean,
    val verifiedAt: Long?,
    val createdAt: Long
)
