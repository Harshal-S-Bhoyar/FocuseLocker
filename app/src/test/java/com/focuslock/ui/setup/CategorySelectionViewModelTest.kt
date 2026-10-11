package com.focuslock.ui.setup

import com.focuslock.domain.model.BlockedCategory
import com.focuslock.data.local.entity.CategoryKey
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.ToggleCategoryUseCase
import com.focuslock.data.apps.AppBlockSelectionStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent

@OptIn(ExperimentalCoroutinesApi::class)
class CategorySelectionViewModelTest {

    private val getAllCategoriesUseCase = mockk<GetAllCategoriesUseCase>()
    private val toggleCategoryUseCase = mockk<ToggleCategoryUseCase>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    
    private val mockContext = mockk<android.content.Context>()
    private val mockPrefs = mockk<android.content.SharedPreferences>()
    private val mockEditor = mockk<android.content.SharedPreferences.Editor>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mockContext.getSharedPreferences("focuslock_prefs", android.content.Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.getBoolean("auto_mode_enabled", false) } returns false
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `categories flow emits values from use case`() = runTest {
        val categoryList = listOf(BlockedCategory(id = 1, categoryKey = CategoryKey.ADULT, isActive = false))
        every { getAllCategoriesUseCase() } returns flowOf(categoryList)

        val viewModel = CategorySelectionViewModel(getAllCategoriesUseCase, toggleCategoryUseCase, AppBlockSelectionStore(), mockContext)

        // Start collecting so SharingStarted.WhileSubscribed triggers
        val collectJob = launch {
            viewModel.categories.collect {}
        }
        
        runCurrent()

        assertEquals(categoryList, viewModel.categories.value)
        collectJob.cancel()
    }

    @Test
    fun `onCategoryToggled calls use case`() = runTest {
        every { getAllCategoriesUseCase() } returns flowOf(emptyList())
        val viewModel = CategorySelectionViewModel(getAllCategoriesUseCase, toggleCategoryUseCase, AppBlockSelectionStore(), mockContext)

        viewModel.onCategoryToggled(CategoryKey.ADULT, true)

        coVerify { toggleCategoryUseCase(CategoryKey.ADULT, true) }
    }

    @Test
    fun `toggleAutoMode updates preferences and state`() = runTest {
        val categoryList = listOf(
            BlockedCategory(id = 1, categoryKey = CategoryKey.ADULT, isActive = false),
            BlockedCategory(id = 2, categoryKey = CategoryKey.GAMING, isActive = false)
        )
        every { getAllCategoriesUseCase() } returns flowOf(categoryList)
        val viewModel = CategorySelectionViewModel(getAllCategoriesUseCase, toggleCategoryUseCase, AppBlockSelectionStore(), mockContext)

        val collectJob = launch { viewModel.autoMode.collect {} }
        runCurrent()
        
        assertEquals(false, viewModel.autoMode.value)

        viewModel.onAutoModeToggled(true)
        runCurrent()

        assertEquals(true, viewModel.autoMode.value)
        io.mockk.verify { mockEditor.putBoolean("auto_mode_enabled", true) }
        io.mockk.verify { mockEditor.apply() }
        
        com.focuslock.data.local.entity.CategoryKey.ALL
            .filter { it != com.focuslock.data.local.entity.CategoryKey.AUTO_BLOCKED }
            .forEach {
                coVerify { toggleCategoryUseCase(it, true) }
            }

        collectJob.cancel()
    }
}
