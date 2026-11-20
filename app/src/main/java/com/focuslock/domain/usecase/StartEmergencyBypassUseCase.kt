package com.focuslock.domain.usecase

import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.data.repository.UnlockRepository
import com.focuslock.data.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class StartEmergencyBypassUseCase @Inject constructor(
    private val unlockRepository: UnlockRepository,
    private val workScheduler: WorkScheduler
) {
    suspend operator fun invoke(sessionId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val latestRequest = unlockRepository.getLatestRequestSync(sessionId)
            if (latestRequest?.status == UnlockRequestStatus.EMERGENCY) {
                return@withContext Result.failure(Exception("Emergency bypass already active"))
            }

            // Create a new request to track the emergency bypass
            val requestId = unlockRepository.createRequest(sessionId)
            unlockRepository.updateStatus(requestId, UnlockRequestStatus.EMERGENCY)
            
            // 24 hours in milliseconds
            val delayMillis = 24 * 60 * 60 * 1000L
            val endsAt = System.currentTimeMillis() + delayMillis
            unlockRepository.setCooldownEndsAt(requestId, endsAt)
            workScheduler.scheduleEmergencyBypass(endsAt)
            
            Result.success(Unit)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error starting emergency bypass")
            Result.failure(e)
        }
    }
}
