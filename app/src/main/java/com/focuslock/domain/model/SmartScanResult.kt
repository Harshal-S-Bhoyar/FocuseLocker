package com.focuslock.domain.model

sealed class SmartScanResult {
    data class Blocked(val keyword: String, val signal: String) : SmartScanResult()
    object Clean : SmartScanResult()
    data class FetchFailed(val reason: String) : SmartScanResult()
}
