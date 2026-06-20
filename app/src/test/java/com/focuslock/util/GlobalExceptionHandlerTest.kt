package com.focuslock.util

import io.mockk.*
import org.junit.Test
import timber.log.Timber

class GlobalExceptionHandlerTest {

    @Test
    fun `uncaughtException should log fatal exception via Timber and delegate to default handler`() {
        // Arrange
        val mockDefaultHandler = mockk<Thread.UncaughtExceptionHandler>(relaxed = true)
        val globalExceptionHandler = GlobalExceptionHandler(mockDefaultHandler)
        
        val testThread = Thread.currentThread()
        val testException = RuntimeException("Test Fatal Crash")
        
        // Mock Timber to verify it's called
        mockkObject(Timber)
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
        
        // Act
        globalExceptionHandler.uncaughtException(testThread, testException)
        
        // Assert
        verify { Timber.e(testException, "FATAL UNCAUGHT EXCEPTION in thread ${testThread.name}") }
        verify { mockDefaultHandler.uncaughtException(testThread, testException) }
        
        unmockkObject(Timber)
    }
}
