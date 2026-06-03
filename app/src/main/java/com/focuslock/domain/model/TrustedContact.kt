package com.focuslock.domain.model

data class TrustedContact(
    val id: Long,
    val email: String,
    val isVerified: Boolean,
    val verifiedAt: Long?,
    val createdAt: Long
)
