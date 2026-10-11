package com.focuslock.domain.usecase

import com.focuslock.data.apps.AppBlockSelectionStore
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.apps.InstalledAppsProvider
import com.focuslock.domain.model.InstalledApp
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlockingUseCaseTest {

    private val fakeApps = listOf(
        InstalledApp("com.example.youtube", "YouTube"),
        InstalledApp("com.example.insta", "Instagram"),
        InstalledApp("com.example.notes", "Notes")
    )
    private val fakeProvider = object : InstalledAppsProvider {
        override suspend fun getLaunchableApps(): List<InstalledApp> = fakeApps
    }

    @Test
    fun `GetLaunchableAppsUseCase returns all when query blank`() = runTest {
        assertEquals(fakeApps, GetLaunchableAppsUseCase(fakeProvider)(""))
    }

    @Test
    fun `GetLaunchableAppsUseCase filters by label and package`() = runTest {
        val useCase = GetLaunchableAppsUseCase(fakeProvider)
        assertEquals(listOf(fakeApps[0]), useCase("tube"))
        assertEquals(listOf(fakeApps[1]), useCase("insta"))
        assertTrue(useCase("no-match").isEmpty())
    }

    @Test
    fun `AppBlockSelectionStore toggle ON OFF and clear`() = runTest {
        val store = AppBlockSelectionStore()
        assertTrue(store.selected.value.isEmpty())

        store.toggle("com.example.youtube", true)
        assertEquals(setOf("com.example.youtube"), store.selected.value)

        store.toggle("com.example.youtube", false)
        assertTrue(store.selected.value.isEmpty())

        store.toggle("a", true)
        store.toggle("b", true)
        store.clear()
        assertTrue(store.selected.value.isEmpty())
    }

    @Test
    fun `IsAppBlockedUseCase reflects active session keys`() = runTest {
        val repo = mockk<SessionRepository>(relaxed = true)
        coEvery { repo.getBlockedAppPackagesSync() } returns setOf("com.example.youtube")
        val useCase = IsAppBlockedUseCase(repo)

        assertTrue(useCase("com.example.youtube"))
        assertFalse(useCase("com.example.notes"))
        assertFalse(useCase(""))
    }

    @Test
    fun `IsAppBlockedUseCase empty selection blocks nothing`() = runTest {
        val repo = mockk<SessionRepository>(relaxed = true)
        coEvery { repo.getBlockedAppPackagesSync() } returns emptySet()
        val useCase = IsAppBlockedUseCase(repo)

        assertFalse(useCase("com.example.youtube"))
    }
}
