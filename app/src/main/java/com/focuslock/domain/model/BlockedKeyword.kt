package com.focuslock.domain.model

data class BlockedKeyword(
    val id: Long,
    val keyword: String, // Always lowercase
    val isActive: Boolean,
    val createdAt: Long
)
