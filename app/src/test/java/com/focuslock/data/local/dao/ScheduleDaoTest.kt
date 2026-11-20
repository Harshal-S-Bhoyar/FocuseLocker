package com.focuslock.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.ScheduleWindowEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleDaoTest {

    private lateinit var database: FocusDatabase
    private lateinit var scheduleDao: ScheduleDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        scheduleDao = database.scheduleDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `upsert and read back ordered by weekday and start`() = runBlocking {
        scheduleDao.upsertWindow(ScheduleWindowEntity(weekday = 5, startMinutes = 1200, endMinutes = 840))
        scheduleDao.upsertWindow(ScheduleWindowEntity(weekday = 1, startMinutes = 1200, endMinutes = 840))
        scheduleDao.upsertWindow(ScheduleWindowEntity(weekday = 1, startMinutes = 60, endMinutes = 120))

        val windows = scheduleDao.getAllWindowsSync()
        assertEquals(3, windows.size)
        assertEquals(1, windows[0].weekday)
        assertEquals(60, windows[0].startMinutes)
        assertEquals(1, windows[1].weekday)
        assertEquals(1200, windows[1].startMinutes)
        assertEquals(5, windows[2].weekday)
    }

    @Test
    fun `delete window removes only that row`() = runBlocking {
        val id = scheduleDao.upsertWindow(ScheduleWindowEntity(weekday = 6, startMinutes = 0, endMinutes = 1439))
        scheduleDao.upsertWindow(ScheduleWindowEntity(weekday = 7, startMinutes = 0, endMinutes = 1439))

        scheduleDao.deleteWindow(id)

        val windows = scheduleDao.getAllWindowsSync()
        assertEquals(1, windows.size)
        assertEquals(7, windows[0].weekday)
    }

    @Test
    fun `flow emits inserted windows`() = runBlocking {
        scheduleDao.upsertWindow(ScheduleWindowEntity(weekday = 3, startMinutes = 480, endMinutes = 600))

        val windows = scheduleDao.getAllWindows().first()
        assertEquals(1, windows.size)
        assertTrue(windows[0].id > 0)
    }

    @Test
    fun `delete all clears template`() = runBlocking {
        scheduleDao.upsertWindow(ScheduleWindowEntity(weekday = 1, startMinutes = 0, endMinutes = 100))
        scheduleDao.deleteAllWindows()

        assertTrue(scheduleDao.getAllWindowsSync().isEmpty())
    }
}
