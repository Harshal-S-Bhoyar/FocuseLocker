package com.focuslock.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.BlockedCategoryEntity
import com.focuslock.data.local.entity.BlockedDomainEntity
import com.focuslock.data.local.entity.CategoryKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// ponytail: basic CRUD testing. Ceiling is large DB size which we don't simulate here. 
// Upgrade path: add benchmark tests for >10k domains if UI jank appears.

@RunWith(AndroidJUnit4::class)
class BlockedDomainDaoTest {

    private lateinit var database: FocusDatabase
    private lateinit var blockedDomainDao: BlockedDomainDao
    private lateinit var blockedCategoryDao: BlockedCategoryDao // We need this to insert category first

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        blockedDomainDao = database.blockedDomainDao()
        blockedCategoryDao = database.blockedCategoryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun getActiveBlockedDomainList() = runBlocking {
        // Insert active category
        blockedCategoryDao.insertCategory(BlockedCategoryEntity(categoryKey = CategoryKey.SOCIAL_MEDIA, isActive = true))
        
        // Insert domains
        val now = System.currentTimeMillis()
        blockedDomainDao.insertDomain(BlockedDomainEntity(domain = "facebook.com", categoryKey = CategoryKey.SOCIAL_MEDIA, blockStatus = "PERMANENT"))
        blockedDomainDao.insertDomain(BlockedDomainEntity(domain = "expired.com", categoryKey = CategoryKey.SOCIAL_MEDIA, blockStatus = "TEMPORARY", expiresAt = now - 1000))
        blockedDomainDao.insertDomain(BlockedDomainEntity(domain = "valid.com", categoryKey = CategoryKey.SOCIAL_MEDIA, blockStatus = "TEMPORARY", expiresAt = now + 10000))

        val activeDomains = blockedDomainDao.getActiveBlockedDomainList(now)
        
        assertEquals(2, activeDomains.size)
        assertEquals(true, activeDomains.contains("facebook.com"))
        assertEquals(true, activeDomains.contains("valid.com"))
        assertEquals(false, activeDomains.contains("expired.com"))
    }
    
    @Test
    fun deleteExpiredEntries() = runBlocking {
        blockedCategoryDao.insertCategory(BlockedCategoryEntity(categoryKey = CategoryKey.SOCIAL_MEDIA, isActive = true))
        val now = System.currentTimeMillis()
        blockedDomainDao.insertDomain(BlockedDomainEntity(domain = "facebook.com", categoryKey = CategoryKey.SOCIAL_MEDIA, blockStatus = "PERMANENT"))
        blockedDomainDao.insertDomain(BlockedDomainEntity(domain = "expired.com", categoryKey = CategoryKey.SOCIAL_MEDIA, blockStatus = "TEMPORARY", expiresAt = now - 1000))
        
        blockedDomainDao.deleteExpiredEntries(now)
        
        val count = blockedDomainDao.getDomainCount()
        assertEquals(1, count)
        
        val activeDomains = blockedDomainDao.getActiveBlockedDomainList(now)
        assertEquals(true, activeDomains.contains("facebook.com"))
    }
}
