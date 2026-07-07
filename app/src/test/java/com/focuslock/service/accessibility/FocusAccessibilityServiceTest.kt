package com.focuslock.service.accessibility

import android.content.Context
import com.focuslock.di.BlockingServiceEntryPoint
import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.model.BlockReason
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.usecase.CheckUrlForBlockedContentUseCase
import com.focuslock.domain.usecase.GetAllBlockedDomainsFlowUseCase
import com.focuslock.domain.usecase.GetActiveKeywordsUseCase
import com.focuslock.domain.usecase.DoHQueryUseCase
import com.focuslock.domain.usecase.KeywordScanner
import com.focuslock.domain.usecase.SmartScanUseCase
import dagger.hilt.android.EntryPointAccessors
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

class FocusAccessibilityServiceTest {

    private lateinit var checkUrlForBlockedContentUseCase: CheckUrlForBlockedContentUseCase
    private lateinit var getActiveKeywordsUseCase: GetActiveKeywordsUseCase
    private lateinit var getAllBlockedDomainsFlowUseCase: GetAllBlockedDomainsFlowUseCase
    private lateinit var sessionRepository: SessionRepository
    private lateinit var smartScanUseCase: SmartScanUseCase
    private lateinit var dohQueryUseCase: DoHQueryUseCase
    private lateinit var keywordScanner: KeywordScanner

    @Before
    fun setup() {
        checkUrlForBlockedContentUseCase = mockk()
        getActiveKeywordsUseCase = mockk()
        getAllBlockedDomainsFlowUseCase = mockk()
        sessionRepository = mockk()
        smartScanUseCase = mockk()
        dohQueryUseCase = mockk()
        keywordScanner = mockk(relaxed = true)

        every { getActiveKeywordsUseCase() } returns flowOf(listOf("distraction"))
        coEvery { getAllBlockedDomainsFlowUseCase() } returns flowOf(listOf("example.com"))
        every { sessionRepository.getActiveSession() } returns flowOf(null)

        mockkStatic(EntryPointAccessors::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onServiceConnected initializes all use cases including smartScanUseCase`() = runTest {
        // Arrange
        val service = spyk(FocusAccessibilityService(), recordPrivateCalls = true)
        every { service["updateForegroundState"](any<Boolean>()) } returns Unit
        
        val mockContext = mockk<Context>(relaxed = true)
        every { service.applicationContext } returns mockContext

        val entryPoint = mockk<BlockingServiceEntryPoint>()
        every { entryPoint.checkUrlForBlockedContentUseCase() } returns checkUrlForBlockedContentUseCase
        every { entryPoint.getActiveKeywordsUseCase() } returns getActiveKeywordsUseCase
        every { entryPoint.getAllBlockedDomainsFlowUseCase() } returns getAllBlockedDomainsFlowUseCase
        every { entryPoint.sessionRepository() } returns sessionRepository
        every { entryPoint.smartScanUseCase() } returns smartScanUseCase
        every { entryPoint.dohQueryUseCase() } returns dohQueryUseCase
        every { entryPoint.keywordScanner() } returns keywordScanner

        every { EntryPointAccessors.fromApplication(any(), BlockingServiceEntryPoint::class.java) } returns entryPoint

        // Act
        // Use reflection to call the protected onServiceConnected method
        val onServiceConnectedMethod = FocusAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
        onServiceConnectedMethod.isAccessible = true
        onServiceConnectedMethod.invoke(service)

        // Assert
        // Use reflection to verify the lateinit properties are initialized
        assertFieldInitialized(service, "smartScanUseCase")
        assertFieldInitialized(service, "checkUrlForBlockedContentUseCase")
        assertFieldInitialized(service, "getActiveKeywordsUseCase")
        assertFieldInitialized(service, "getAllBlockedDomainsFlowUseCase")
        assertFieldInitialized(service, "sessionRepository")
        assertFieldInitialized(service, "dohQueryUseCase")
        assertFieldInitialized(service, "keywordScanner")

        verify { service["updateForegroundState"](true) }
    }

    private fun assertFieldInitialized(target: Any, fieldName: String) {
        val field: Field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        val value = field.get(target)
        assertNotNull("Field $fieldName should be initialized but was null", value)
    }

    @Test
    fun `when settingsGuardMode is true and user leaves settings, it forces overlay back`() = runTest {
        // Arrange
        val service = spyk(FocusAccessibilityService(), recordPrivateCalls = true)
        
        // Mock isSessionActive to true
        val isSessionActiveField = FocusAccessibilityService::class.java.getDeclaredField("isSessionActive")
        isSessionActiveField.isAccessible = true
        isSessionActiveField.set(service, true)

        FocusAccessibilityService.settingsGuardMode = true

        mockkObject(com.focuslock.service.overlay.PermissionEnforcementService)
        every { com.focuslock.service.overlay.PermissionEnforcementService.start(any()) } returns Unit

        val mockEvent = mockk<android.view.accessibility.AccessibilityEvent>()
        every { mockEvent.packageName } returns "com.google.android.youtube" // User went to YouTube
        every { mockEvent.eventType } returns android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        every { service.packageName } returns "com.focuslock"

        // Act
        service.onAccessibilityEvent(mockEvent)

        // Assert
        verify { com.focuslock.service.overlay.PermissionEnforcementService.start(service) }
        org.junit.Assert.assertFalse("settingsGuardMode should be reset", FocusAccessibilityService.settingsGuardMode)
        
        unmockkAll() // clear static mocks
    }

    @Test
    fun `when settingsGuardMode is true and user stays in settings, it does not force overlay`() = runTest {
        // Arrange
        val service = spyk(FocusAccessibilityService(), recordPrivateCalls = true)
        
        val isSessionActiveField = FocusAccessibilityService::class.java.getDeclaredField("isSessionActive")
        isSessionActiveField.isAccessible = true
        isSessionActiveField.set(service, true)

        FocusAccessibilityService.settingsGuardMode = true

        mockkObject(com.focuslock.service.overlay.PermissionEnforcementService)
        every { com.focuslock.service.overlay.PermissionEnforcementService.start(any()) } returns Unit

        val mockEvent = mockk<android.view.accessibility.AccessibilityEvent>()
        every { mockEvent.packageName } returns "com.android.settings" // User is still in settings
        every { mockEvent.eventType } returns android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        every { service.packageName } returns "com.focuslock"

        // Act
        service.onAccessibilityEvent(mockEvent)

        // Assert
        verify(exactly = 0) { com.focuslock.service.overlay.PermissionEnforcementService.start(any()) }
        org.junit.Assert.assertTrue("settingsGuardMode should remain active", FocusAccessibilityService.settingsGuardMode)
        
        unmockkAll()
    }
}
