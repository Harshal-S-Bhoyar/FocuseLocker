package com.focuslock.domain.usecase

import com.focuslock.data.repository.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * UseCase to get the remaining time for the active session.
 * Emits the remaining milliseconds every second until the session expires.
 */
class GetSessionRemainingTimeUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<Long> = flow {
        val session = sessionRepository.getActiveSessionSync()
        if (session == null) {
            emit(0L)
            return@flow
        }
        
        var remaining = session.expiresAt - System.currentTimeMillis()
        while (remaining > 0) {
            emit(remaining)
            delay(1000L) // Delay for 1 second before the next emission
            remaining = session.expiresAt - System.currentTimeMillis()
        }
        emit(0L) // Final emission when expired
    }
}
