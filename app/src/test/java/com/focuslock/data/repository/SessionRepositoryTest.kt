package com.focuslock.data.repository

import com.focuslock.data.local.dao.SessionDao
import com.focuslock.data.local.entity.SessionEntity
import com.focuslock.data.local.entity.SessionStatus as EntityStatus
import com.focuslock.domain.model.SessionStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionRepositoryTest {

    private lateinit var sessionDao: SessionDao
    private lateinit var repository: SessionRepository

    @Before
    fun setup() {
        sessionDao = mockk(relaxed = true)
        repository = SessionRepository(sessionDao)
    }

    @Test
    fun `getActiveSession returns mapped flow`() = runTest {
        val entity = SessionEntity(
            id = 1L,
            status = EntityStatus.ACTIVE,
            startedAt = 1000L,
            expiresAt = 2000L,
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = 1000L
        )
        every { sessionDao.getSessionByStatus(EntityStatus.ACTIVE) } returns flowOf(entity)

        val session = repository.getActiveSession().first()

        assertEquals(1L, session?.id)
        assertEquals(SessionStatus.ACTIVE, session?.status)
    }

    @Test
    fun `getActiveSessionSync returns mapped entity`() = runTest {
        val entity = SessionEntity(
            id = 1L,
            status = EntityStatus.COMPLETED,
            startedAt = 1000L,
            expiresAt = 2000L,
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = 1000L
        )
        coEvery { sessionDao.getActiveSessionSync() } returns entity

        val session = repository.getActiveSessionSync()

        assertEquals(1L, session?.id)
        assertEquals(SessionStatus.COMPLETED, session?.status)
    }

    @Test
    fun `getActiveSessionSync returns null when no session`() = runTest {
        coEvery { sessionDao.getActiveSessionSync() } returns null

        val session = repository.getActiveSessionSync()

        assertNull(session)
    }

    @Test
    fun `startSession inserts entity with current time`() = runTest {
        coEvery { sessionDao.insertSession(any()) } returns 5L

        val id = repository.startSession(5000L, emptyList())

        assertEquals(5L, id)
        coVerify { 
            sessionDao.insertSession(withArg { 
                assertEquals(EntityStatus.ACTIVE, it.status)
                assertEquals(5000L, it.expiresAt)
                assertFalse(it.unlockedEarly)
                assertNull(it.unlockRequestedAt)
            }) 
        }
    }

    @Test
    fun `endSession updates status correctly`() = runTest {
        repository.endSession(1L, SessionStatus.COMPLETED)
        coVerify { sessionDao.updateSessionStatus(1L, EntityStatus.COMPLETED) }

        repository.endSession(1L, SessionStatus.CANCELLED)
        coVerify { sessionDao.updateSessionStatus(1L, EntityStatus.CANCELLED) }
    }

    @Test
    fun `markUnlockRequested updates timestamp`() = runTest {
        repository.markUnlockRequested(1L, 9999L)
        coVerify { sessionDao.updateUnlockRequestedAt(1L, 9999L) }
    }

    @Test
    fun `hasActiveSession returns true when not expired`() = runTest {
        val futureTime = System.currentTimeMillis() + 100000L
        val entity = SessionEntity(
            id = 1L,
            status = EntityStatus.ACTIVE,
            startedAt = 1000L,
            expiresAt = futureTime,
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = 1000L
        )
        coEvery { sessionDao.getActiveSessionSync() } returns entity

        val isActive = repository.hasActiveSession()

        assertTrue(isActive)
    }

    @Test
    fun `hasActiveSession returns false when expired`() = runTest {
        val pastTime = System.currentTimeMillis() - 10000L
        val entity = SessionEntity(
            id = 1L,
            status = EntityStatus.ACTIVE,
            startedAt = 1000L,
            expiresAt = pastTime,
            unlockedEarly = false,
            unlockRequestedAt = null,
            createdAt = 1000L
        )
        coEvery { sessionDao.getActiveSessionSync() } returns entity

        val isActive = repository.hasActiveSession()

        assertFalse(isActive)
    }

    @Test
    fun `hasActiveSession returns false when no session`() = runTest {
        coEvery { sessionDao.getActiveSessionSync() } returns null

        val isActive = repository.hasActiveSession()

        assertFalse(isActive)
    }
}
