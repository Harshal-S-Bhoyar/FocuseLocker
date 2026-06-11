package com.focuslock.domain.model

sealed class DoHResult {
    object Blocked : DoHResult()
    object Clean : DoHResult()
    object Unknown : DoHResult()
    object QueryFailed : DoHResult()
}
