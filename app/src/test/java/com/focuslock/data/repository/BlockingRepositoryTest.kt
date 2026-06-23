package com.focuslock.data.repository

import android.content.Context
import com.focuslock.data.local.dao.BlockedCategoryDao
import com.focuslock.data.local.dao.BlockedDomainDao
import com.focuslock.data.local.dao.BlockedKeywordDao
import com.focuslock.data.local.entity.BlockedCategoryEntity
import com.focuslock.data.local.entity.BlockedDomainEntity
import com.focuslock.data.local.entity.BlockedKeywordEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlockingRepositoryTest {

    private lateinit var keywordDao: BlockedKeywordDao
    private lateinit var categoryDao: BlockedCategoryDao
    private lateinit var domainDao: BlockedDomainDao
    private lateinit var context: Context
    private lateinit var repository: BlockingRepository

    @Before
    fun setup() {
        keywordDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        domainDao = mockk(relaxed = true)
        context = mockk(relaxed = true)
        repository = BlockingRepository(keywordDao, categoryDao, domainDao, context)
    }

    @Test
    fun `addKeyword normalizes string and adds if under limit`() = runTest {
        coEvery { keywordDao.getKeywordCount() } returns 5
        coEvery { keywordDao.keywordExists("test") } returns false

        val result = repository.addKeyword(" TeSt ")

        assertTrue(result.isSuccess)
        coVerify { 
            keywordDao.insertKeyword(withArg { 
                assertEquals("test", it.keyword)
                assertTrue(it.isActive)
            }) 
        }
    }

    @Test
    fun `addKeyword fails if blank`() = runTest {
        val result = repository.addKeyword("   ")
        assertTrue(result.isFailure)
        assertEquals("Keyword cannot be blank", result.exceptionOrNull()?.message)
    }

    @Test
    fun `addKeyword fails if limit reached`() = runTest {
        coEvery { keywordDao.getKeywordCount() } returns 10
        val result = repository.addKeyword("test")
        assertTrue(result.isFailure)
        assertEquals("Maximum 10 keywords reached", result.exceptionOrNull()?.message)
    }

    @Test
    fun `addKeyword fails if already exists`() = runTest {
        coEvery { keywordDao.getKeywordCount() } returns 5
        coEvery { keywordDao.keywordExists("test") } returns true
        val result = repository.addKeyword("test")
        assertTrue(result.isFailure)
        assertEquals("Keyword already exists", result.exceptionOrNull()?.message)
    }

    @Test
    fun `removeKeyword delegates to dao`() = runTest {
        repository.removeKeyword(5L)
        coVerify { keywordDao.deleteKeyword(5L) }
    }

    @Test
    fun `getActiveKeywords maps correctly`() = runTest {
        val entity = BlockedKeywordEntity(1L, "test", true, 1000L)
        every { keywordDao.getActiveKeywords() } returns flowOf(listOf(entity))

        val list = repository.getActiveKeywords().first()
        assertEquals(1, list.size)
        assertEquals("test", list[0].keyword)
    }

    @Test
    fun `setCategoryActive delegates to dao`() = runTest {
        repository.setCategoryActive("SOCIAL", true)
        coVerify { categoryDao.updateCategoryStatus("SOCIAL", true) }
    }

    @Test
    fun `getActiveCategories maps correctly`() = runTest {
        val entity = BlockedCategoryEntity(1L, "SOCIAL", true)
        every { categoryDao.getActiveCategories() } returns flowOf(listOf(entity))

        val list = repository.getActiveCategories().first()
        assertEquals(1, list.size)
        assertEquals("SOCIAL", list[0].categoryKey)
    }

    @Test
    fun `addAutoBlockedDomain inserts domain correctly`() = runTest {
        repository.addAutoBlockedDomain("example.com", "exam", "CLEAN_CACHE", 12345L)
        
        coVerify { 
            domainDao.upsertDomain(withArg { 
                assertEquals("example.com", it.domain)
                assertEquals("AUTO_BLOCKED", it.categoryKey)
                assertTrue(it.isAutoBlocked)
                assertEquals("exam", it.matchedKeyword)
                assertEquals("CLEAN_CACHE", it.blockStatus)
                assertEquals(12345L, it.expiresAt)
            }) 
        }
    }

    @Test
    fun `getCachedDomainStatus returns null when not found`() = runTest {
        coEvery { domainDao.findActiveDomain("example.com", any()) } returns null
        val status = repository.getCachedDomainStatus("example.com")
        assertEquals(null, status)
    }

    @Test
    fun `getCachedDomainStatus returns status when found`() = runTest {
        val entity = BlockedDomainEntity(1L, "example.com", "AUTO_BLOCKED", true, "", 0L, "PERMANENT", null)
        coEvery { domainDao.findActiveDomain("example.com", any()) } returns entity
        val status = repository.getCachedDomainStatus("example.com")
        assertEquals("PERMANENT", status)
    }
}
