package com.focuslock.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.BlockedKeywordEntity
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
class BlockedKeywordDaoTest {

    private lateinit var database: FocusDatabase
    private lateinit var dao: BlockedKeywordDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.blockedKeywordDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and get active keywords`() = runBlocking {
        val keyword1 = BlockedKeywordEntity(
            keyword = "porn",
            isActive = true,
            createdAt = 1000L
        )
        val keyword2 = BlockedKeywordEntity(
            keyword = "gambling",
            isActive = false,
            createdAt = 1000L
        )
        
        dao.insertKeyword(keyword1)
        dao.insertKeyword(keyword2)

        val activeKeywords = dao.getActiveKeywords().first()
        assertEquals(1, activeKeywords.size)
        assertEquals("porn", activeKeywords[0].keyword)
        
        assertTrue(dao.keywordExists("gambling"))
    }

    @Test
    fun `update keyword status`() = runBlocking {
        val keyword = BlockedKeywordEntity(
            keyword = "distraction",
            isActive = false,
            createdAt = 1000L
        )
        val id = dao.insertKeyword(keyword)
        
        dao.updateKeywordStatus(id, true)
        
        val activeKeywords = dao.getActiveKeywords().first()
        assertEquals(1, activeKeywords.size)
        assertEquals("distraction", activeKeywords[0].keyword)
    }

    @Test
    fun `get all keywords returns alphabetical order`() = runBlocking {
        dao.insertKeyword(BlockedKeywordEntity(keyword = "zeta", isActive = true, createdAt = 1L))
        dao.insertKeyword(BlockedKeywordEntity(keyword = "alpha", isActive = false, createdAt = 2L))

        val keywords = dao.getAllKeywords().first()

        assertEquals(listOf("alpha", "zeta"), keywords.map { it.keyword })
    }

    @Test
    fun `duplicate keyword is ignored and original remains`() = runBlocking {
        val firstId = dao.insertKeyword(BlockedKeywordEntity(keyword = "repeat", isActive = false, createdAt = 1L))
        val duplicateId = dao.insertKeyword(BlockedKeywordEntity(keyword = "repeat", isActive = true, createdAt = 2L))

        assertTrue(firstId > 0)
        assertEquals(-1L, duplicateId)
        assertEquals(1, dao.getKeywordCount())
        assertEquals(false, dao.getAllKeywords().first().single().isActive)
    }

    @Test
    fun `delete keyword removes only requested record`() = runBlocking {
        val deleteId = dao.insertKeyword(BlockedKeywordEntity(keyword = "remove", isActive = true, createdAt = 1L))
        dao.insertKeyword(BlockedKeywordEntity(keyword = "keep", isActive = true, createdAt = 2L))

        dao.deleteKeyword(deleteId)

        assertEquals(1, dao.getKeywordCount())
        assertTrue(!dao.keywordExists("remove"))
        assertTrue(dao.keywordExists("keep"))
    }
}
