package com.focuslock.domain.session

/**
 * Controls Android runtime services at the edge of the domain layer.
 * Implementations must keep framework dependencies outside session use cases.
 */
interface SessionRuntimeController {
    fun startRuntime(remainingMillis: Long)
    fun stopRuntime()
}
