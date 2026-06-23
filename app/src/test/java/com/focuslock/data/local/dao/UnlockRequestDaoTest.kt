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
}
