package com.focuslock.integration

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.entity.CategoryKey
import com.focuslock.data.repository.BlockingRepository
import com.focuslock.domain.model.DoHResult
import com.focuslock.domain.usecase.DoHQueryUseCase
import com.focuslock.domain.usecase.GetAllCategoriesUseCase
import com.focuslock.domain.usecase.ToggleCategoryUseCase
import com.focuslock.ui.setup.CategorySelectionViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class CategoryIntegrationTest {

    private lateinit var context: Context
    private val blockingRepository = mockk<BlockingRepository>(relaxed = true)
    private val configRepository = mockk<com.focuslock.domain.repository.ConfigRepository>(relaxed = true)
    private val dohRepository = mockk<com.focuslock.domain.repository.DoHRepository>(relaxed = true)
    private val getAllCategoriesUseCase = mockk<GetAllCategoriesUseCase>(relaxed = true)
    private val toggleCategoryUseCase = mockk<ToggleCategoryUseCase>(relaxed = true)
    
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        context = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(testDispatcher)

        // Clear shared preferences
        context.getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `Integration Flow - Toggle auto mode updates categories and controls network DNS queries`() = runTest {
        // Setup initial flow of categories
        every { getAllCategoriesUseCase() } returns flowOf(emptyList())

        val viewModel = CategorySelectionViewModel(
            getAllCategoriesUseCase,
            toggleCategoryUseCase,
            context
        )

        // Set up DoHQueryUseCase with mocked HTTP response to simulate a clean domain
        val doHQueryUseCase = DoHQueryUseCase(blockingRepository, configRepository, dohRepository)
        coEvery { dohRepository.queryDoH(any()) } returns DoHResult.Clean
        coEvery { blockingRepository.getCachedDomainStatus(any()) } returns null // Not cached
        var autoModeEnabled = false
        coEvery { configRepository.isAutoModeEnabled() } answers { autoModeEnabled }

        // -------------------------------------------------------------
        // Step 1: Initial state (auto mode off).
        // checkAndCacheDoH should bypass network and return Clean directly.
        // -------------------------------------------------------------
        assertEquals(false, viewModel.autoMode.value)
        val initialResult = doHQueryUseCase.checkAndCacheDoH("example.com")
        
        assertEquals(DoHResult.Clean, initialResult)
        coVerify(exactly = 0) { blockingRepository.getCachedDomainStatus(any()) }
        coVerify(exactly = 0) { dohRepository.queryDoH(any()) }

        // -------------------------------------------------------------
        // Step 2: Toggle auto mode ON via ViewModel.
        // This should activate preferences and trigger use cases.
        // -------------------------------------------------------------
        viewModel.onAutoModeToggled(true)
        runCurrent()
        coEvery { configRepository.isAutoModeEnabled() } returns true
        
        assertEquals(true, viewModel.autoMode.value)

        // Verify that toggleCategoryUseCase was called for all categories except AUTO_BLOCKED
        CategoryKey.ALL.filter { it != CategoryKey.AUTO_BLOCKED }.forEach {
            coVerify { toggleCategoryUseCase(it, true) }
        }

        // -------------------------------------------------------------
        // Step 3: Check DoH with auto mode ON.
        // checkAndCacheDoH should now query the network/repository.
        // -------------------------------------------------------------
        val activeResult = doHQueryUseCase.checkAndCacheDoH("example.com")
        
        assertEquals(DoHResult.Clean, activeResult)
        // Ensure repository and network were actually queried
        coVerify(exactly = 1) { blockingRepository.getCachedDomainStatus("example.com") }
        coVerify(exactly = 1) { dohRepository.queryDoH(any()) }
    }
}
