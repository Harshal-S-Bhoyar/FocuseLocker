package com.focuslock.domain.usecase

import com.focuslock.data.repository.TrustedContactRepository
import javax.inject.Inject

class HasVerifiedContactUseCase @Inject constructor(
    private val trustedContactRepository: TrustedContactRepository
) {
    suspend operator fun invoke(): Boolean {
        return trustedContactRepository.hasVerifiedContact()
    }
}
