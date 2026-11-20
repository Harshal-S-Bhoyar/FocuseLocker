package com.focuslock.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.ScheduleWindowEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleRepositoryTest {

    private lateinit var database: FocusDatabase
    private lateinit var repository: ScheduleRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ScheduleRepository(database.scheduleDao(), database.appSettingDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `template round trip through upsert read delete`() = runBlocking {
        val id = repository.upsertWindow(ScheduleWindowEntity(weekday = 1, startMinutes = 1200, endMinutes = 840))
        assertTrue(id > 0)
        assertEquals(1, repository.getWindowsSync().size)
        assertEquals(1, repository.getWindows().first().size)

        repository.deleteWindow(id)
        assertTrue(repository.getWindowsSync().isEmpty())
    }

    @Test
    fun `switch defaults off and toggles`() = runBlocking {
        assertFalse(repository.isScheduleEnabledSync())
        assertEquals(false, repository.isScheduleEnabled().first())

        repository.setScheduleEnabled(true)
        assertTrue(repository.isScheduleEnabledSync())
        assertEquals(true, repository.isScheduleEnabled().first())

        repository.setScheduleEnabled(false)
        assertFalse(repository.isScheduleEnabledSync())
    }

    @Test
    fun `delete all clears every weekday`() = runBlocking {
        repository.upsertWindow(ScheduleWindowEntity(weekday = 1, startMinutes = 0, endMinutes = 100))
        repository.upsertWindow(ScheduleWindowEntity(weekday = 7, startMinutes = 0, endMinutes = 100))
        repository.deleteAllWindows()

        assertTrue(repository.getWindowsSync().isEmpty())
    }
}
