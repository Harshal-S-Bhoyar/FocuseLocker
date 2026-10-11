package com.focuslock.domain.usecase

import com.focuslock.data.repository.SessionRepository
import javax.inject.Inject

class IsAppBlockedUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return sessionRepository.getBlockedAppPackagesSync().contains(packageName)
    }
}
