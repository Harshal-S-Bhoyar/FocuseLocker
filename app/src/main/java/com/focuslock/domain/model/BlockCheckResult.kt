package com.focuslock.domain.model

sealed class BlockCheckResult {
    data class Blocked(val matchedTerm: String, val reason: BlockReason) : BlockCheckResult()
    object Allowed : BlockCheckResult()
}

enum class BlockReason { 
    KEYWORD_MATCH, 
    DOMAIN_BLOCKED,
    APP_BLOCKED 
}
