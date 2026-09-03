package com.focuslock.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingDaoTest {

    private lateinit var database: FocusDatabase
    private lateinit var dao: AppSettingDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        ).allowMainThreadQueries().build()
        
        dao = database.appSettingDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and get setting`() = runBlocking {
        val setting = AppSettingEntity("test_key", "test_value")
        dao.insertSetting(setting)

        val retrieved = dao.getSetting("test_key").first()
        assertEquals("test_value", retrieved?.value)
    }

    @Test
    fun `getSettingValue returns correct value`() = runBlocking {
        val setting = AppSettingEntity("test_key", "test_value")
        dao.insertSetting(setting)

        val value = dao.getSettingValue("test_key")
        assertEquals("test_value", value)
    }

    @Test
    fun `deleteSetting removes setting`() = runBlocking {
        val setting = AppSettingEntity("test_key", "test_value")
        dao.insertSetting(setting)
        
        dao.deleteSetting("test_key")
        
        val value = dao.getSettingValue("test_key")
        assertNull(value)
    }

    @Test
    fun `updateSettingValue updates correctly`() = runBlocking {
        val setting = AppSettingEntity("test_key", "old_value")
        dao.insertSetting(setting)
        
        dao.updateSettingValue("test_key", "new_value")
        
        val value = dao.getSettingValue("test_key")
        assertEquals("new_value", value)
    }

    @Test
    fun `getSettingValue returns null for missing key`() = runBlocking {
        assertNull(dao.getSettingValue("missing_key"))
    }

    @Test
    fun `insertSetting replaces an existing key`() = runBlocking {
        dao.insertSetting(AppSettingEntity("test_key", "first_value"))
        dao.insertSetting(AppSettingEntity("test_key", "second_value"))

        assertEquals("second_value", dao.getSettingValue("test_key"))
    }
}
