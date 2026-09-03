package com.focuslock.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.SessionEntity
import com.focuslock.data.local.entity.SessionStatus
import com.focuslock.data.local.entity.UnlockRequestEntity
import com.focuslock.data.local.entity.UnlockRequestStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnlockRequestDaoTest {

    private lateinit var database: FocusDatabase
    private lateinit var sessionDao: SessionDao
    private lateinit var dao: UnlockRequestDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        sessionDao = database.sessionDao()
        dao = database.unlockRequestDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and get latest request`() = runBlocking {
        sessionDao.insertSession(SessionEntity(id = 1L, status = SessionStatus.ACTIVE, startedAt = 1000L, expiresAt = 5000L, createdAt = 1000L))
        val request1 = UnlockRequestEntity(
            sessionId = 1L,
            status = UnlockRequestStatus.PENDING,
            requestedAt = 1000L,
            cooldownEndsAt = null
        )
        val request2 = UnlockRequestEntity(
            sessionId = 1L,
            status = UnlockRequestStatus.COMPLETED,
            requestedAt = 2000L,
            cooldownEndsAt = null
        )
        dao.insertRequest(request1)
        dao.insertRequest(request2)

        val latest = dao.getLatestRequestBySession(1L).first()
        assertNotNull(latest)
        assertEquals(UnlockRequestStatus.COMPLETED, latest?.status)
    }

    @Test
    fun `increment otp attempts`() = runBlocking {
        sessionDao.insertSession(SessionEntity(id = 1L, status = SessionStatus.ACTIVE, startedAt = 1000L, expiresAt = 5000L, createdAt = 1000L))
        val request = UnlockRequestEntity(
            sessionId = 1L,
            status = UnlockRequestStatus.PENDING,
            requestedAt = 1000L,
            cooldownEndsAt = null,
            otpAttempts = 0
        )
        val id = dao.insertRequest(request)
        
        dao.incrementOtpAttempts(id)
        dao.incrementOtpAttempts(id)
        
        val attempts = dao.getOtpAttempts(id)
        assertEquals(2, attempts)
    }

    @Test
    fun `requests by session are ordered newest first`() = runBlocking {
        sessionDao.insertSession(SessionEntity(id = 1L, status = SessionStatus.ACTIVE, startedAt = 1L, expiresAt = 2L, createdAt = 1L))
        dao.insertRequest(UnlockRequestEntity(sessionId = 1L, status = UnlockRequestStatus.PENDING, requestedAt = 10L, cooldownEndsAt = null))
        dao.insertRequest(UnlockRequestEntity(sessionId = 1L, status = UnlockRequestStatus.COMPLETED, requestedAt = 20L, cooldownEndsAt = null))

        val requests = dao.getRequestsBySession(1L).first()

        assertEquals(listOf(20L, 10L), requests.map { it.requestedAt })
    }

    @Test
    fun `update request status and cooldown fields`() = runBlocking {
        sessionDao.insertSession(SessionEntity(id = 1L, status = SessionStatus.ACTIVE, startedAt = 1L, expiresAt = 2L, createdAt = 1L))
        val id = dao.insertRequest(UnlockRequestEntity(sessionId = 1L, status = UnlockRequestStatus.PENDING, requestedAt = 10L, cooldownEndsAt = null))

        dao.updateRequestStatus(id, UnlockRequestStatus.COMPLETED)
        dao.updateCooldownEndsAt(id, 99L)

        val request = dao.getRequestById(id).first()
        assertEquals(UnlockRequestStatus.COMPLETED, request?.status)
        assertEquals(99L, request?.cooldownEndsAt)
    }

    @Test
    fun `delete requests by session preserves other session requests`() = runBlocking {
        sessionDao.insertSession(SessionEntity(id = 1L, status = SessionStatus.ACTIVE, startedAt = 1L, expiresAt = 2L, createdAt = 1L))
        sessionDao.insertSession(SessionEntity(id = 2L, status = SessionStatus.ACTIVE, startedAt = 3L, expiresAt = 4L, createdAt = 3L))
        dao.insertRequest(UnlockRequestEntity(sessionId = 1L, status = UnlockRequestStatus.PENDING, requestedAt = 10L, cooldownEndsAt = null))
        dao.insertRequest(UnlockRequestEntity(sessionId = 2L, status = UnlockRequestStatus.PENDING, requestedAt = 20L, cooldownEndsAt = null))

        dao.deleteRequestsBySession(1L)

        assertEquals(emptyList<UnlockRequestEntity>(), dao.getRequestsBySession(1L).first())
        assertEquals(1, dao.getRequestsBySession(2L).first().size)
    }
}
