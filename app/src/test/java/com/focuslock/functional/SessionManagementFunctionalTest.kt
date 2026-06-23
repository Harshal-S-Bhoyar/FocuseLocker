package com.focuslock.functional

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.worker.SessionWorkerScheduler
import com.focuslock.domain.model.SessionStatus
import com.focuslock.domain.session.SessionRuntimeController
import com.focuslock.domain.usecase.ActivateSessionUseCase
import com.focuslock.domain.usecase.CleanupStaleSessionUseCase
import com.focuslock.domain.usecase.DeactivateSessionUseCase
import com.focuslock.domain.usecase.GetSessionRemainingTimeUseCase
import com.focuslock.domain.usecase.IsSessionActiveUseCase
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionManagementFunctionalTest {

    private lateinit var database: FocusDatabase
    private lateinit var sessionRepository: SessionRepository

    private lateinit var sessionWorkerScheduler: SessionWorkerScheduler
    private lateinit var sessionRuntimeController: SessionRuntimeController

    private lateinit var activateSessionUseCase: ActivateSessionUseCase
    private lateinit var deactivateSessionUseCase: DeactivateSessionUseCase
    private lateinit var cleanupStaleSessionUseCase: CleanupStaleSessionUseCase
    private lateinit var isSessionActiveUseCase: IsSessionActiveUseCase
    private lateinit var getSessionRemainingTimeUseCase: GetSessionRemainingTimeUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            FocusDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        sessionRepository = SessionRepository(database.sessionDao())
        
        sessionWorkerScheduler = mockk(relaxed = true)
        sessionRuntimeController = mockk(relaxed = true)

        deactivateSessionUseCase = DeactivateSessionUseCase(
            sessionRepository,
            sessionWorkerScheduler,
            sessionRuntimeController
        )

        cleanupStaleSessionUseCase = CleanupStaleSessionUseCase(
            sessionRepository,
            deactivateSessionUseCase
        )

        isSessionActiveUseCase = IsSessionActiveUseCase(
            sessionRepository,
            cleanupStaleSessionUseCase
        )

        activateSessionUseCase = ActivateSessionUseCase(
            sessionRepository,
            sessionWorkerScheduler,
            sessionRuntimeController,
            cleanupStaleSessionUseCase
        )

        getSessionRemainingTimeUseCase = GetSessionRemainingTimeUseCase(sessionRepository)
    }

    @After
    fun teardown() {
        database.close()
        clearAllMocks()
    }

    @Test
    fun testSessionLifecycleFlow() = runBlocking {
        // Step 1: No active session initially
        assertFalse("Session should not be active initially", isSessionActiveUseCase())

        // Step 2: Start a valid session (e.g., 30 minutes)
        val durationMillis = 30 * 60 * 1000L
        val activationResult = activateSessionUseCase(durationMillis)
        
        assertTrue("Session activation should succeed", activationResult.isSuccess)
        val sessionId = activationResult.getOrNull()
        assertNotNull(sessionId)

        // Verify side effects
        verify(exactly = 1) { sessionWorkerScheduler.scheduleSessionTimers(any()) }
        verify(exactly = 1) { sessionRuntimeController.startRuntime(durationMillis) }

        // Verify state
        assertTrue("Session should be active", isSessionActiveUseCase())
        
        val remainingTime = getSessionRemainingTimeUseCase().first()
        assertTrue("Remaining time should be greater than 0", remainingTime > 0)
        assertTrue("Remaining time should be approx duration", remainingTime <= durationMillis)

        // Step 3: Attempt to start another session while one is active
        val secondActivationResult = activateSessionUseCase(15 * 60 * 1000L)
        assertTrue("Should not allow starting a new session while one is active", secondActivationResult.isFailure)

        // Step 4: Deactivate the active session manually
        val deactivationResult = deactivateSessionUseCase(sessionId!!, SessionStatus.COMPLETED)
        assertTrue("Deactivation should succeed", deactivationResult.isSuccess)

        // Verify side effects of deactivation
        verify(exactly = 1) { sessionWorkerScheduler.cancelSessionTimers() }
        verify(exactly = 1) { sessionRuntimeController.stopRuntime() }

        // Verify state is clean
        assertFalse("Session should no longer be active", isSessionActiveUseCase())
        val finalRemainingTime = getSessionRemainingTimeUseCase().first()
        assertEquals("Remaining time should be 0", 0L, finalRemainingTime)
    }
}
