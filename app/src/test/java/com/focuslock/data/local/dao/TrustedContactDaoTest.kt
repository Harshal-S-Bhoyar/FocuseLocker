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

    @Test
    fun `verified contact query excludes unverified contact`() = runBlocking {
        dao.insertContact(
            TrustedContactEntity(email = "pending@example.com", isVerified = false, verifiedAt = null, createdAt = 1L)
        )

        assertEquals(null, dao.getVerifiedContact().first())
    }

    @Test
    fun `insert with same id replaces existing contact`() = runBlocking {
        val id = dao.insertContact(
            TrustedContactEntity(email = "old@example.com", isVerified = false, verifiedAt = null, createdAt = 1L)
        )
        dao.insertContact(
            TrustedContactEntity(id = id, email = "new@example.com", isVerified = true, verifiedAt = 2L, createdAt = 3L)
        )

        val retrieved = dao.getContactSync()
        assertEquals(1, dao.getContactCount())
        assertEquals("new@example.com", retrieved?.email)
        assertTrue(retrieved?.isVerified == true)
    }

    @Test
    fun `delete all contacts clears contact and verified queries`() = runBlocking {
        dao.insertContact(
            TrustedContactEntity(email = "one@example.com", isVerified = true, verifiedAt = 1L, createdAt = 1L)
        )
        dao.insertContact(
            TrustedContactEntity(email = "two@example.com", isVerified = false, verifiedAt = null, createdAt = 2L)
        )

        dao.deleteAllContacts()

        assertEquals(0, dao.getContactCount())
        assertEquals(null, dao.getContactSync())
        assertEquals(null, dao.getVerifiedContact().first())
    }
}
