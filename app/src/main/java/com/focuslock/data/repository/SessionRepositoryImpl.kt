package com.focuslock.data.repository

import com.focuslock.data.local.dao.SessionDao
import com.focuslock.data.local.entity.SessionEntity
import com.focuslock.data.local.entity.SessionStatus as EntityStatus
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {

    override fun getActiveSession(): Flow<Session?> {
        return sessionDao.getSessionByStatus(EntityStatus.ACTIVE)
            .map { it?.toDomain() }
    }

    override suspend fun getActiveSessionSync(): Session? {
        return sessionDao.getActiveSessionSync()?.toDomain()
    }

    override suspend fun startSession(expiresAt: Long): Long {
        val now = System.currentTimeMillis()
        val entity = SessionEntity(
            status = EntityStatus.ACTIVE,
            startedAt = now,
            expiresAt = expiresAt,
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = now
        )
        return sessionDao.insertSession(entity)
    }

    override suspend fun endSession(sessionId: Long, status: SessionStatus) {
        sessionDao.updateSessionStatus(sessionId, status.toEntityStatus())
    }

    override suspend fun markUnlockRequested(sessionId: Long, timestamp: Long) {
        sessionDao.updateUnlockRequestedAt(sessionId, timestamp)
    }

    override suspend fun hasActiveSession(): Boolean {
        return sessionDao.countSessionsByStatus(EntityStatus.ACTIVE) > 0
    }

    private fun SessionEntity.toDomain(): Session {
        return Session(
            id = id,
            status = when (status) {
                EntityStatus.ACTIVE    -> SessionStatus.ACTIVE
                EntityStatus.COMPLETED -> SessionStatus.COMPLETED
                else                   -> SessionStatus.CANCELLED
            },
            startedAt = startedAt,
            expiresAt = expiresAt,
            unlockedEarly = unlockedEarly,
            unlockRequestedAt = unlockRequestedAt,
            createdAt = createdAt
        )
    }

    private fun SessionStatus.toEntityStatus(): String = when (this) {
        SessionStatus.ACTIVE    -> EntityStatus.ACTIVE
        SessionStatus.COMPLETED -> EntityStatus.COMPLETED
        SessionStatus.CANCELLED -> EntityStatus.CANCELLED
    }
}
