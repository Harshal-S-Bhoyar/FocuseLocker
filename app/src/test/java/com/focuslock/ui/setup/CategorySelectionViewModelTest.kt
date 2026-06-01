package com.focuslock.ui.setup

import com.focuslock.domain.model.BlockedCategory
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.ToggleCategoryUseCase
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `categories flow emits values from use case`() = runTest {
        val categoryList = listOf(BlockedCategory(id = 1, categoryKey = "adult", isActive = false))
        every { getAllCategoriesUseCase() } returns flowOf(categoryList)

        val viewModel = CategorySelectionViewModel(getAllCategoriesUseCase, toggleCategoryUseCase)

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
        val viewModel = CategorySelectionViewModel(getAllCategoriesUseCase, toggleCategoryUseCase)

        viewModel.onCategoryToggled("adult", true)

        coVerify { toggleCategoryUseCase("adult", true) }
    }
}
