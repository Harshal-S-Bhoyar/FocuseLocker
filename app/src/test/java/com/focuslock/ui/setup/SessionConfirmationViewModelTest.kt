package com.focuslock.ui.setup

import androidx.lifecycle.SavedStateHandle
import com.focuslock.data.repository.TrustedContactRepository
import com.focuslock.domain.model.BlockedCategory
import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.domain.model.TrustedContact
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionConfirmationViewModelTest {

    private val savedStateHandle = SavedStateHandle(mapOf("durationMillis" to 3600000L))
    private val getAllCategoriesUseCase = mockk<GetAllCategoriesUseCase>()
    private val getAllKeywordsUseCase = mockk<GetAllKeywordsUseCase>()
    private val trustedContactRepository = mockk<TrustedContactRepository>()
    private val activateSessionUseCase = mockk<ActivateSessionUseCase>()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { getAllCategoriesUseCase() } returns flowOf(emptyList())
        every { getAllKeywordsUseCase() } returns flowOf(emptyList())
        every { trustedContactRepository.getVerifiedContact() } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lockInSession updates state to Success on success`() = runTest {
        coEvery { activateSessionUseCase(any()) } returns Result.success(1L)
        val viewModel = SessionConfirmationViewModel(
            savedStateHandle,
            getAllCategoriesUseCase,
            getAllKeywordsUseCase,
            trustedContactRepository,
            activateSessionUseCase
        )

        viewModel.lockInSession()

        assertTrue(viewModel.activationState.value is SessionConfirmationViewModel.ActivationState.Success)
    }

    @Test
    fun `lockInSession updates state to Error on failure`() = runTest {
        coEvery { activateSessionUseCase(any()) } returns Result.failure(Exception("Activation failed"))
        val viewModel = SessionConfirmationViewModel(
            savedStateHandle,
            getAllCategoriesUseCase,
            getAllKeywordsUseCase,
            trustedContactRepository,
            activateSessionUseCase
        )

        viewModel.lockInSession()

        val state = viewModel.activationState.value
        assertTrue(state is SessionConfirmationViewModel.ActivationState.Error)
        assertEquals("Activation failed", (state as SessionConfirmationViewModel.ActivationState.Error).message)
    }

    @Test
    fun `missing duration uses the one minute fallback`() {
        val viewModel = SessionConfirmationViewModel(
            SavedStateHandle(),
            getAllCategoriesUseCase,
            getAllKeywordsUseCase,
            trustedContactRepository,
            activateSessionUseCase
        )

        assertEquals(60_000L, viewModel.durationMillis)
    }

    @Test
    fun `activeCategories filters inactive categories`() = runTest {
        val active = BlockedCategory(1L, "NEWS", true)
        val inactive = BlockedCategory(2L, "GAMING", false)
        every { getAllCategoriesUseCase() } returns flowOf(listOf(active, inactive))
        val viewModel = SessionConfirmationViewModel(
            savedStateHandle,
            getAllCategoriesUseCase,
            getAllKeywordsUseCase,
            trustedContactRepository,
            activateSessionUseCase
        )
        val collectJob = launch { viewModel.activeCategories.collect {} }

        runCurrent()

        assertEquals(listOf(active), viewModel.activeCategories.value)
        collectJob.cancel()
    }

    @Test
    fun `keywords and verified contact flows are preserved`() = runTest {
        val keyword = BlockedKeyword(4L, "study", true, 100L)
        val contact = TrustedContact(5L, "guardian@example.com", true, 200L, 100L)
        every { getAllKeywordsUseCase() } returns flowOf(listOf(keyword))
        every { trustedContactRepository.getVerifiedContact() } returns flowOf(contact)
        val viewModel = SessionConfirmationViewModel(
            savedStateHandle,
            getAllCategoriesUseCase,
            getAllKeywordsUseCase,
            trustedContactRepository,
            activateSessionUseCase
        )
        val keywordJob = launch { viewModel.keywords.collect {} }
        val contactJob = launch { viewModel.trustedContact.collect {} }

        runCurrent()

        assertEquals(listOf(keyword), viewModel.keywords.value)
        assertEquals(contact, viewModel.trustedContact.value)
        keywordJob.cancel()
        contactJob.cancel()
    }

    @Test
    fun `lockInSession ignores a second request while activation is loading`() = runTest {
        val gate = CompletableDeferred<Result<Long>>()
        coEvery { activateSessionUseCase(any()) } coAnswers { gate.await() }
        val viewModel = SessionConfirmationViewModel(
            savedStateHandle,
            getAllCategoriesUseCase,
            getAllKeywordsUseCase,
            trustedContactRepository,
            activateSessionUseCase
        )

        viewModel.lockInSession()
        runCurrent()
        viewModel.lockInSession()

        assertTrue(viewModel.activationState.value is SessionConfirmationViewModel.ActivationState.Loading)
        coVerify(exactly = 1) { activateSessionUseCase(3_600_000L) }

        gate.complete(Result.success(1L))
        runCurrent()
    }

    @Test
    fun `null activation error message uses the fallback text`() = runTest {
        coEvery { activateSessionUseCase(any()) } returns Result.failure(Exception())
        val viewModel = SessionConfirmationViewModel(
            savedStateHandle,
            getAllCategoriesUseCase,
            getAllKeywordsUseCase,
            trustedContactRepository,
            activateSessionUseCase
        )

        viewModel.lockInSession()

        val state = viewModel.activationState.value
        assertTrue(state is SessionConfirmationViewModel.ActivationState.Error)
        assertEquals(
            "An unknown error occurred while activating the session.",
            (state as SessionConfirmationViewModel.ActivationState.Error).message
        )
    }
}
