package com.focuslock.ui.setup

import androidx.lifecycle.SavedStateHandle
import com.focuslock.data.local.entity.ScheduleWindowEntity
import com.focuslock.data.repository.ScheduleRepository
import com.focuslock.data.repository.TrustedContactRepository
import com.focuslock.domain.model.BlockedCategory
import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.domain.model.Session
import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.model.TrustedContact
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.ActivationResult
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.GetAllKeywordsUseCase
import com.focuslock.domain.usecase.ScheduleEngine
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
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val scheduleEngine = mockk<ScheduleEngine>()
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun viewModel(handle: SavedStateHandle = savedStateHandle) = SessionConfirmationViewModel(
        handle,
        getAllCategoriesUseCase,
        getAllKeywordsUseCase,
        trustedContactRepository,
        activateSessionUseCase,
        scheduleRepository,
        scheduleEngine
    )

    private fun runningSession() = Session(
        id = 2L,
        status = SessionStatus.ACTIVE,
        startedAt = 0L,
        expiresAt = Long.MAX_VALUE,
        unlockedEarly = false,
        unlockRequestedAt = null,
        createdAt = 0L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { getAllCategoriesUseCase() } returns flowOf(emptyList())
        every { getAllKeywordsUseCase() } returns flowOf(emptyList())
        every { trustedContactRepository.getVerifiedContact() } returns flowOf(null)
        coEvery { scheduleRepository.getWindowsSync() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lockInSession updates state to Success on success`() = runTest {
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.Activated(1L, 0L)
        val viewModel = viewModel()

        viewModel.lockInSession()

        assertTrue(viewModel.activationState.value is SessionConfirmationViewModel.ActivationState.Success)
    }

    @Test
    fun `lockInSession updates state to Error on failure`() = runTest {
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.Failed(Exception("Activation failed"))
        val viewModel = viewModel()

        viewModel.lockInSession()

        val state = viewModel.activationState.value
        assertTrue(state is SessionConfirmationViewModel.ActivationState.Error)
        assertEquals("Activation failed", (state as SessionConfirmationViewModel.ActivationState.Error).message)
    }

    @Test
    fun `live window with a running session enters it through the atomic already-active outcome`() = runTest {
        // The use case is the single place that decides "a session is already running"; the live
        // hand-off just enters that session, so a second session is never created.
        coEvery { scheduleRepository.getWindowsSync() } returns listOf(
            ScheduleWindowEntity(1L, 1, 0, 60)
        )
        every { scheduleEngine.dueWindow(any(), any(), any()) } returns ScheduleWindowEntity(1L, 1, 0, 60)
        every { scheduleEngine.remainingMillis(any(), any()) } returns 1_800_000L
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.AlreadyActive(runningSession())
        val viewModel = viewModel(SavedStateHandle(mapOf("durationMillis" to 3600000L, "live" to true)))

        viewModel.lockInSession()

        assertTrue(viewModel.activationState.value is SessionConfirmationViewModel.ActivationState.Success)
        coVerify(exactly = 1) { activateSessionUseCase(1_800_000L) }
    }

    @Test
    fun `one-shot start with an unrelated running session still requests the chosen duration`() = runTest {
        // Regression: the shortcut used to apply to every entry path, silently adopting the running
        // session and discarding the duration the user chose here.
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.AlreadyActive(runningSession())
        val viewModel = viewModel()

        viewModel.lockInSession()

        assertTrue(viewModel.activationState.value is SessionConfirmationViewModel.ActivationState.Success)
        coVerify(exactly = 1) { activateSessionUseCase(3600000L) }
    }

    @Test
    fun `missing duration uses the one minute fallback`() {
        val viewModel = SessionConfirmationViewModel(
            SavedStateHandle(),
            getAllCategoriesUseCase,
            getAllKeywordsUseCase,
            trustedContactRepository,
            activateSessionUseCase,
            scheduleRepository,
            scheduleEngine
        )

        assertEquals(60_000L, viewModel.durationMillis)
    }

    @Test
    fun `activeCategories filters inactive categories`() = runTest {
        val active = BlockedCategory(1L, "NEWS", true)
        val inactive = BlockedCategory(2L, "GAMING", false)
        every { getAllCategoriesUseCase() } returns flowOf(listOf(active, inactive))
        val viewModel = viewModel()
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
        val viewModel = viewModel()
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
        val gate = CompletableDeferred<ActivationResult>()
        coEvery { activateSessionUseCase(any()) } coAnswers { gate.await() }
        val viewModel = viewModel()

        viewModel.lockInSession()
        runCurrent()
        viewModel.lockInSession()

        assertTrue(viewModel.activationState.value is SessionConfirmationViewModel.ActivationState.Loading)
        coVerify(exactly = 1) { activateSessionUseCase(3_600_000L) }

        gate.complete(ActivationResult.Activated(1L, 0L))
        runCurrent()
    }

    @Test
    fun `null activation error message uses the fallback text`() = runTest {
        coEvery { activateSessionUseCase(any()) } returns ActivationResult.Failed(Exception())
        val viewModel = viewModel()

        viewModel.lockInSession()

        val state = viewModel.activationState.value
        assertTrue(state is SessionConfirmationViewModel.ActivationState.Error)
        assertEquals(
            "An unknown error occurred while activating the session.",
            (state as SessionConfirmationViewModel.ActivationState.Error).message
        )
    }
}
