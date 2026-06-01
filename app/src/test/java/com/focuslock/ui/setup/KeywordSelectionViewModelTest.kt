package com.focuslock.ui.setup

import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.domain.usecase.AddKeywordUseCase
import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import com.focuslock.domain.usecase.RemoveKeywordUseCase
import io.mockk.coEvery
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

@OptIn(ExperimentalCoroutinesApi::class)
class KeywordSelectionViewModelTest {

    private val getAllKeywordsUseCase = mockk<GetAllKeywordsUseCase>()
    private val addKeywordUseCase = mockk<AddKeywordUseCase>()
    private val removeKeywordUseCase = mockk<RemoveKeywordUseCase>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { getAllKeywordsUseCase() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addKeyword updates error on failure`() = runTest {
        coEvery { addKeywordUseCase("distraction") } returns Result.failure(Exception("Limit reached"))
        val viewModel = KeywordSelectionViewModel(getAllKeywordsUseCase, addKeywordUseCase, removeKeywordUseCase)

        viewModel.addKeyword("distraction")

        assertEquals("Limit reached", viewModel.error.value)
    }

    @Test
    fun `addKeyword clears error on success`() = runTest {
        coEvery { addKeywordUseCase("distraction") } returns Result.success(Unit)
        val viewModel = KeywordSelectionViewModel(getAllKeywordsUseCase, addKeywordUseCase, removeKeywordUseCase)
        
        // Setup initial error
        coEvery { addKeywordUseCase("error") } returns Result.failure(Exception("Limit reached"))
        viewModel.addKeyword("error")
        assertEquals("Limit reached", viewModel.error.value)

        // Add valid keyword
        viewModel.addKeyword("distraction")

        assertEquals(null, viewModel.error.value)
        coVerify { addKeywordUseCase("distraction") }
    }

    @Test
    fun `removeKeyword calls use case and clears error`() = runTest {
        val viewModel = KeywordSelectionViewModel(getAllKeywordsUseCase, addKeywordUseCase, removeKeywordUseCase)

        viewModel.removeKeyword(1L)

        coVerify { removeKeywordUseCase(1L) }
        assertEquals(null, viewModel.error.value)
    }
}
