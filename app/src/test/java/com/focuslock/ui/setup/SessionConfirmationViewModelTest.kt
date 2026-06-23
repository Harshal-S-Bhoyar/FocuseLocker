package com.focuslock.ui.setup

import androidx.lifecycle.SavedStateHandle
import com.focuslock.data.repository.TrustedContactRepository
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import io.mockk.coEvery
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
}
