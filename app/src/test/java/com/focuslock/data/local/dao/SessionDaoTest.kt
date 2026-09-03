package com.focuslock.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.SessionEntity
import com.focuslock.data.local.entity.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionDaoTest {

    private lateinit var database: FocusDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.sessionDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and get active session sync`() = runBlocking {
        val session = SessionEntity(
            status = SessionStatus.ACTIVE,
            startedAt = 1000L,
            expiresAt = 5000L,
            unlockedEarly = false,
            createdAt = 1000L,
            unlockRequestedAt = null
        )
        val id = dao.insertSession(session)
        val retrieved = dao.getActiveSessionSync(SessionStatus.ACTIVE)
        
        assertNotNull(retrieved)
        assertEquals(id, retrieved?.id)
        assertEquals(SessionStatus.ACTIVE, retrieved?.status)
    }

    @Test
    fun `update session status`() = runBlocking {
        val session = SessionEntity(
            status = SessionStatus.ACTIVE,
            startedAt = 1000L,
            expiresAt = 5000L,
            unlockedEarly = false,
            createdAt = 1000L,
            unlockRequestedAt = null
        )
        val id = dao.insertSession(session)
        
        dao.updateSessionStatus(id, SessionStatus.COMPLETED)
        
        val retrieved = dao.getSessionById(id).first()
        assertEquals(SessionStatus.COMPLETED, retrieved?.status)
    }

    @Test
    fun `getSessionByStatusFlow emits latest state`() = runBlocking {
        val session = SessionEntity(
            status = SessionStatus.ACTIVE,
            startedAt = 1000L,
            expiresAt = 2000L,
            unlockedEarly = false,
            createdAt = 1000L,
            unlockRequestedAt = null
        )
        val id = dao.insertSession(session)

        val flowEmittedSession = dao.getSessionByStatus(SessionStatus.ACTIVE).first()
        assertNotNull(flowEmittedSession)
        assertEquals(id, flowEmittedSession?.id)

        dao.updateSessionStatus(id, SessionStatus.CANCELLED)
        val activeSessionAfterCancel = dao.getSessionByStatus(SessionStatus.ACTIVE).first()
        assertNull(activeSessionAfterCancel)
    }

    @Test
    fun `get all sessions returns newest created session first`() = runBlocking {
        dao.insertSession(SessionEntity(status = SessionStatus.COMPLETED, startedAt = 1L, expiresAt = 2L, unlockedEarly = false, createdAt = 10L, unlockRequestedAt = null))
        dao.insertSession(SessionEntity(status = SessionStatus.CANCELLED, startedAt = 3L, expiresAt = 4L, unlockedEarly = false, createdAt = 20L, unlockRequestedAt = null))

        val sessions = dao.getAllSessions().first()

        assertEquals(listOf(20L, 10L), sessions.map { it.createdAt })
    }

    @Test
    fun `update unlock requested timestamp persists on session`() = runBlocking {
        val id = dao.insertSession(SessionEntity(status = SessionStatus.ACTIVE, startedAt = 1L, expiresAt = 2L, unlockedEarly = false, createdAt = 1L, unlockRequestedAt = null))

        dao.updateUnlockRequestedAt(id, 99L)

        assertEquals(99L, dao.getSessionById(id).first()?.unlockRequestedAt)
    }

    @Test
    fun `delete session removes it and updates status count`() = runBlocking {
        val id = dao.insertSession(SessionEntity(status = SessionStatus.ACTIVE, startedAt = 1L, expiresAt = 2L, unlockedEarly = false, createdAt = 1L, unlockRequestedAt = null))

        assertEquals(1, dao.countSessionsByStatus(SessionStatus.ACTIVE))
        dao.deleteSession(id)

        assertNull(dao.getSessionById(id).first())
        assertEquals(0, dao.countSessionsByStatus(SessionStatus.ACTIVE))
    }
}
