package com.focuslock.ui.setup

import com.focuslock.data.repository.MAX_KEYWORDS
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun `keywords flow exposes repository emissions`() = runTest {
        val expected = listOf(BlockedKeyword(1L, "study", true, 10L))
        every { getAllKeywordsUseCase() } returns flowOf(expected)
        val viewModel = KeywordSelectionViewModel(getAllKeywordsUseCase, addKeywordUseCase, removeKeywordUseCase)
        val collectJob = launch { viewModel.keywords.collect {} }

        runCurrent()

        assertEquals(expected, viewModel.keywords.value)
        collectJob.cancel()
    }

    @Test
    fun `clearError removes an existing add failure`() = runTest {
        coEvery { addKeywordUseCase("blocked") } returns Result.failure(Exception("Limit reached"))
        val viewModel = KeywordSelectionViewModel(getAllKeywordsUseCase, addKeywordUseCase, removeKeywordUseCase)

        viewModel.addKeyword("blocked")
        assertEquals("Limit reached", viewModel.error.value)

        viewModel.clearError()

        assertEquals(null, viewModel.error.value)
    }

    @Test
    fun `removeKeyword clears a prior error even when removal fails`() = runTest {        coEvery { addKeywordUseCase("blocked") } returns Result.failure(Exception("Limit reached"))
        coEvery { removeKeywordUseCase(9L) } returns Result.failure(Exception("Missing keyword"))
        val viewModel = KeywordSelectionViewModel(getAllKeywordsUseCase, addKeywordUseCase, removeKeywordUseCase)

        viewModel.addKeyword("blocked")
        viewModel.removeKeyword(9L)

        assertEquals(null, viewModel.error.value)
        coVerify(exactly = 1) { removeKeywordUseCase(9L) }
    }

    @Test
    fun `ui max matches enforced repository max`() {
        val viewModel = KeywordSelectionViewModel(getAllKeywordsUseCase, addKeywordUseCase, removeKeywordUseCase)

        assertEquals(MAX_KEYWORDS, viewModel.maxKeywords)
    }
}
