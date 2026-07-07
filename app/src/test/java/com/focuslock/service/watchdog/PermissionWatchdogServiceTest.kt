package com.focuslock.service.watchdog

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.data.repository.SessionRepository
import com.focuslock.service.overlay.PermissionEnforcementService
import com.focuslock.util.PermissionChecker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowService

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class PermissionWatchdogServiceTest {

    private lateinit var service: PermissionWatchdogService
    private lateinit var sessionRepository: SessionRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk(relaxed = true)
        
        // Mock permission checkers
        mockkObject(PermissionChecker)
        mockkObject(PermissionEnforcementService)
        
        every { PermissionEnforcementService.start(any()) } returns Unit

        service = Robolectric.buildService(PermissionWatchdogService::class.java).create().get()
        service.sessionRepository = sessionRepository
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `onStartCommand should return START_STICKY`() {
        val intent = Intent()
        val result = service.onStartCommand(intent, 0, 1)
        assertEquals(Service.START_STICKY, result)
    }
}
