package com.focuslock.domain.usecase

import com.focuslock.domain.repository.SessionRepository
import javax.inject.Inject

class IsSessionActiveUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): Boolean {
        return sessionRepository.hasActiveSession()
    }
}
