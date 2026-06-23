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
}
