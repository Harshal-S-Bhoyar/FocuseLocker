package com.focuslock.functional

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.repository.BlockingRepository
import com.focuslock.domain.usecase.AddKeywordUseCase
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import com.focuslock.domain.usecase.RemoveKeywordUseCase
import com.focuslock.domain.usecase.ToggleCategoryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CategoryKeywordFunctionalTest {

    private lateinit var database: FocusDatabase
    private lateinit var blockingRepository: BlockingRepository

    private lateinit var getAllCategoriesUseCase: GetAllCategoriesUseCase
    private lateinit var toggleCategoryUseCase: ToggleCategoryUseCase
    private lateinit var addKeywordUseCase: AddKeywordUseCase
    private lateinit var getAllKeywordsUseCase: GetAllKeywordsUseCase
    private lateinit var removeKeywordUseCase: RemoveKeywordUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        blockingRepository = BlockingRepository(
            keywordDao = database.blockedKeywordDao(),
            categoryDao = database.blockedCategoryDao(),
            domainDao = database.blockedDomainDao(),
            context = context
        )

        getAllCategoriesUseCase = GetAllCategoriesUseCase(blockingRepository)
        toggleCategoryUseCase = ToggleCategoryUseCase(blockingRepository)
        addKeywordUseCase = AddKeywordUseCase(blockingRepository)
        getAllKeywordsUseCase = GetAllKeywordsUseCase(blockingRepository)
        removeKeywordUseCase = RemoveKeywordUseCase(blockingRepository)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testCategoryAndKeywordManagementFlow() = runBlocking {
        // Step 1: Initialize categories
        blockingRepository.seedDomainDataIfNeeded()

        // Step 2: Verify categories are populated and active by default
        val initialCategories = getAllCategoriesUseCase().first()
        assertTrue("Categories should be seeded", initialCategories.isNotEmpty())
        assertTrue("Only AUTO_BLOCKED should be active by default",
            initialCategories.filter { it.isActive }.all { it.categoryKey == "AUTO_BLOCKED" })

        // Step 3: Toggle a category and verify state
        val targetCategory = initialCategories.first { it.categoryKey == "SOCIAL_MEDIA" }
        val toggleResult = toggleCategoryUseCase(targetCategory.categoryKey, false)
        assertTrue(toggleResult.isSuccess)

        val updatedCategories = getAllCategoriesUseCase().first()
        val toggledCategory = updatedCategories.first { it.categoryKey == "SOCIAL_MEDIA" }
        assertFalse("Category should be inactive after toggle", toggledCategory.isActive)

        // Step 4: Add keywords
        val keyword1 = "tiktok"
        val keyword2 = "Instagram" // Should be lowercased
        
        val add1Result = addKeywordUseCase(keyword1)
        val add2Result = addKeywordUseCase(keyword2)
        
        assertTrue(add1Result.isSuccess)
        assertTrue(add2Result.isSuccess)

        // Step 5: Verify keywords are stored and formatted
        var keywords = getAllKeywordsUseCase().first()
        assertEquals("Should have 2 keywords", 2, keywords.size)
        assertTrue("Keyword should be stored", keywords.any { it.keyword == "tiktok" })
        assertTrue("Keyword should be lowercased", keywords.any { it.keyword == "instagram" })

        // Step 6: Empty keyword constraint
        val addEmptyResult = addKeywordUseCase("   ")
        assertTrue("Should not allow empty keywords", addEmptyResult.isFailure)

        // Step 7: Remove a keyword
        val keywordToRemove = keywords.first { it.keyword == "tiktok" }
        removeKeywordUseCase(keywordToRemove.id)

        keywords = getAllKeywordsUseCase().first()
        assertEquals("Should have 1 keyword left", 1, keywords.size)
        assertFalse("Removed keyword should not exist", keywords.any { it.keyword == "tiktok" })
    }

    @Test
    fun `seeding categories twice is idempotent`() = runBlocking {
        blockingRepository.seedDomainDataIfNeeded()
        val first = getAllCategoriesUseCase().first()

        blockingRepository.seedDomainDataIfNeeded()
        val second = getAllCategoriesUseCase().first()

        assertEquals(first.size, second.size)
        assertEquals(first.map { it.categoryKey }.toSet(), second.map { it.categoryKey }.toSet())
    }

    @Test
    fun `a disabled category can be reactivated through the repository flow`() = runBlocking {
        blockingRepository.seedDomainDataIfNeeded()
        val categoryKey = "SOCIAL_MEDIA"

        assertTrue(toggleCategoryUseCase(categoryKey, false).isSuccess)
        assertFalse(getAllCategoriesUseCase().first().first { it.categoryKey == categoryKey }.isActive)

        assertTrue(toggleCategoryUseCase(categoryKey, true).isSuccess)
        assertTrue(getAllCategoriesUseCase().first().first { it.categoryKey == categoryKey }.isActive)
    }

    @Test
    fun `duplicate keywords are rejected after normalization`() = runBlocking {
        assertTrue(addKeywordUseCase("Instagram").isSuccess)

        val duplicate = addKeywordUseCase("  instagram  ")
        val keywords = getAllKeywordsUseCase().first()

        assertTrue(duplicate.isFailure)
        assertEquals(1, keywords.size)
        assertEquals("instagram", keywords.single().keyword)
    }

    @Test
    fun `removing an unknown keyword ID is a successful no-op`() = runBlocking {
        assertTrue(addKeywordUseCase("focus").isSuccess)
        val before = getAllKeywordsUseCase().first()

        val result = removeKeywordUseCase(Long.MAX_VALUE)
        val after = getAllKeywordsUseCase().first()

        assertTrue(result.isSuccess)
        assertEquals(before, after)
    }
}
