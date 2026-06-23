package com.focuslock.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.TrustedContactEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrustedContactDaoTest {

    private lateinit var database: FocusDatabase
    private lateinit var dao: TrustedContactDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.trustedContactDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and get contact sync`() = runBlocking {
        val contact = TrustedContactEntity(
            email = "test@example.com",
            isVerified = false,
            verifiedAt = null,
            createdAt = 1000L
        )
        dao.insertContact(contact)
        
        val retrieved = dao.getContactSync()
        assertNotNull(retrieved)
        assertEquals("test@example.com", retrieved?.email)
    }

    @Test
    fun `update verification status`() = runBlocking {
        val contact = TrustedContactEntity(
            email = "test@example.com",
            isVerified = false,
            verifiedAt = null,
            createdAt = 1000L
        )
        val id = dao.insertContact(contact)
        
        val now = System.currentTimeMillis()
        dao.updateVerificationStatus(id, true, now)
        
        val retrieved = dao.getVerifiedContact().first()
        assertNotNull(retrieved)
        assertTrue(retrieved?.isVerified == true)
        assertEquals(now, retrieved?.verifiedAt)
    }
}
