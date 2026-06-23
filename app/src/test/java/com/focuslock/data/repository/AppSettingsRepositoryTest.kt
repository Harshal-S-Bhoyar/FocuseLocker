package com.focuslock.data.repository

import com.focuslock.data.local.dao.AppSettingDao
import com.focuslock.data.local.entity.AppSettingEntity
import com.focuslock.data.local.entity.AppSettingKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppSettingsRepositoryTest {

    private lateinit var appSettingDao: AppSettingDao
    private lateinit var repository: AppSettingsRepository

    @Before
    fun setup() {
        appSettingDao = mockk(relaxed = true)
        repository = AppSettingsRepository(appSettingDao)
    }

    @Test
    fun `getSetting maps correctly`() = runTest {
        val entity = AppSettingEntity("TEST_KEY", "test_value")
        every { appSettingDao.getSetting("TEST_KEY") } returns flowOf(entity)

        val value = repository.getSetting("TEST_KEY").first()
        assertEquals("test_value", value)
    }

    @Test
    fun `getSettingValue delegates correctly`() = runTest {
        coEvery { appSettingDao.getSettingValue("TEST_KEY") } returns "test_value"

        val value = repository.getSettingValue("TEST_KEY")
        assertEquals("test_value", value)
    }

    @Test
    fun `setSetting inserts correctly`() = runTest {
        repository.setSetting("TEST_KEY", "test_value")
        
        coVerify { 
            appSettingDao.insertSetting(withArg { 
                assertEquals("TEST_KEY", it.key)
                assertEquals("test_value", it.value)
            }) 
        }
    }

    @Test
    fun `initializeDefaultSettings inserts defaults`() = runTest {
        repository.initializeDefaultSettings()

        coVerify { 
            appSettingDao.insertSettings(withArg { defaults ->
                assertEquals(5, defaults.size)
                assertTrue(defaults.any { it.key == AppSettingKey.ONBOARDING_COMPLETE && it.value == "false" })
                assertTrue(defaults.any { it.key == AppSettingKey.DEVICE_ADMIN_ACTIVE && it.value == "false" })
                assertTrue(defaults.any { it.key == AppSettingKey.ACCESSIBILITY_ACTIVE && it.value == "false" })
                assertTrue(defaults.any { it.key == AppSettingKey.VPN_ACTIVE && it.value == "false" })
                assertTrue(defaults.any { it.key == AppSettingKey.FIRST_INSTALL_DATE })
            }) 
        }
    }

    @Test
    fun `isOnboardingComplete returns true when setting is true`() = runTest {
        coEvery { appSettingDao.getSettingValue(AppSettingKey.ONBOARDING_COMPLETE) } returns "true"

        assertTrue(repository.isOnboardingComplete())
    }

    @Test
    fun `isOnboardingComplete returns false when setting is not true`() = runTest {
        coEvery { appSettingDao.getSettingValue(AppSettingKey.ONBOARDING_COMPLETE) } returns "false"
        assertFalse(repository.isOnboardingComplete())
        
        coEvery { appSettingDao.getSettingValue(AppSettingKey.ONBOARDING_COMPLETE) } returns null
        assertFalse(repository.isOnboardingComplete())
    }

    @Test
    fun `setOnboardingComplete updates correctly`() = runTest {
        repository.setOnboardingComplete()
        coVerify { appSettingDao.updateSettingValue(AppSettingKey.ONBOARDING_COMPLETE, "true") }
    }
}
