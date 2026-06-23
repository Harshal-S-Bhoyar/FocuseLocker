package com.focuslock.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.BlockedCategoryEntity
import com.focuslock.data.local.entity.BlockedDomainEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockedDomainDaoTest {

    private lateinit var database: FocusDatabase
    private lateinit var categoryDao: BlockedCategoryDao
    private lateinit var domainDao: BlockedDomainDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        categoryDao = database.blockedCategoryDao()
        domainDao = database.blockedDomainDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `get active blocked domains returns only domains from active categories`() = runBlocking {
        // Insert active category
        categoryDao.insertCategory(
            BlockedCategoryEntity(categoryKey = "SOCIAL", isActive = true)
        )
        // Insert inactive category
        categoryDao.insertCategory(
            BlockedCategoryEntity(categoryKey = "GAMES", isActive = false)
        )

        // Insert domains
        domainDao.insertDomain(
            BlockedDomainEntity(domain = "facebook.com", categoryKey = "SOCIAL", blockStatus = "PERMANENT", isAutoBlocked = false)
        )
        domainDao.insertDomain(
            BlockedDomainEntity(domain = "miniclip.com", categoryKey = "GAMES", blockStatus = "PERMANENT", isAutoBlocked = false)
        )

        val activeList = domainDao.getActiveBlockedDomainList()
        assertEquals(1, activeList.size)
        assertTrue(activeList.contains("facebook.com"))
    }

    @Test
    fun `domainExists returns correct boolean`() = runBlocking {
        categoryDao.insertCategory(BlockedCategoryEntity(categoryKey = "CUSTOM", isActive = true))
        domainDao.insertDomain(
            BlockedDomainEntity(domain = "test.com", categoryKey = "CUSTOM", blockStatus = "PERMANENT", isAutoBlocked = false)
        )
        
        assertTrue(domainDao.domainExists("test.com"))
        assertTrue(!domainDao.domainExists("notfound.com"))
    }

    @Test
    fun `delete expired entries`() = runBlocking {
        categoryDao.insertCategory(BlockedCategoryEntity(categoryKey = "CUSTOM", isActive = true))
        val now = System.currentTimeMillis()
        domainDao.insertDomain(
            BlockedDomainEntity(domain = "expired.com", categoryKey = "CUSTOM", blockStatus = "TEMPORARY", expiresAt = now - 1000, isAutoBlocked = false)
        )
        domainDao.insertDomain(
            BlockedDomainEntity(domain = "active.com", categoryKey = "CUSTOM", blockStatus = "TEMPORARY", expiresAt = now + 5000, isAutoBlocked = false)
        )

        domainDao.deleteExpiredEntries(now)
        
        val count = domainDao.getDomainCount()
        assertEquals(1, count)
        assertTrue(domainDao.domainExists("active.com"))
    }
}
