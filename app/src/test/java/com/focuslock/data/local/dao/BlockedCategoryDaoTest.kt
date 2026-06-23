package com.focuslock.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.BlockedCategoryEntity
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
class BlockedCategoryDaoTest {

    private lateinit var database: FocusDatabase
    private lateinit var dao: BlockedCategoryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.blockedCategoryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and get active categories`() = runBlocking {
        val category1 = BlockedCategoryEntity(
            categoryKey = "SOCIAL",
            isActive = true
        )
        val category2 = BlockedCategoryEntity(
            categoryKey = "GAMES",
            isActive = false
        )
        dao.insertCategory(category1)
        dao.insertCategory(category2)

        val activeCategories = dao.getActiveCategories().first()
        assertEquals(1, activeCategories.size)
        assertEquals("SOCIAL", activeCategories[0].categoryKey)
        
        val activeKeys = dao.getActiveCategoryKeys()
        assertEquals(1, activeKeys.size)
        assertTrue(activeKeys.contains("SOCIAL"))
    }

    @Test
    fun `update category status`() = runBlocking {
        val category = BlockedCategoryEntity(
            categoryKey = "NEWS",
            isActive = false
        )
        dao.insertCategory(category)
        
        dao.updateCategoryStatus("NEWS", true)
        
        val activeCategories = dao.getActiveCategories().first()
        assertEquals(1, activeCategories.size)
        assertEquals("NEWS", activeCategories[0].categoryKey)
    }
}
