package com.focuslock.util

import android.content.Context
import android.util.Log
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.File

class FileLoggingTreeTest {

    private lateinit var mockContext: Context
    private lateinit var mockFilesDir: File
    private lateinit var fileLoggingTree: FileLoggingTree

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.println(any(), any(), any()) } returns 0

        mockContext = mockk()
        
        // Create a temporary directory for logs
        val tempDir = System.getProperty("java.io.tmpdir")
        mockFilesDir = File(tempDir, "focuslock_test_logs")
        mockFilesDir.mkdirs()

        every { mockContext.getExternalFilesDir(null) } returns mockFilesDir

        fileLoggingTree = FileLoggingTree(mockContext)
        Timber.plant(fileLoggingTree)
    }

    @After
    fun tearDown() {
        // Clean up temporary directory
        mockFilesDir.deleteRecursively()
        Timber.uprootAll()
        unmockkStatic(Log::class)
    }

    @Test
    fun `log should write to file in log directory`() {
        // Arrange
        val priority = Log.INFO
        val tag = "TestTag"
        val message = "This is a test message"
        
        // Act
        Timber.tag(tag).i(message)
        fileLoggingTree.flush() // Wait for background write
        
        // Assert
        val logDir = File(mockFilesDir, "log")
        assertTrue("Log directory should exist", logDir.exists())
        
        val logFiles = logDir.listFiles()
        assertTrue("Log file should exist", logFiles != null && logFiles.isNotEmpty())
        
        val logFile = logFiles!!.first()
        val logContent = logFile.readText()
        
        assertTrue("Log content should contain the tag. Actual: $logContent", logContent.contains("I/TestTag"))
        assertTrue("Log content should contain the message. Actual: $logContent", logContent.contains("This is a test message"))
    }

    @Test
    fun `log should format priority correctly`() {
        // Arrange & Act
        Timber.tag("ErrorTag").e(RuntimeException("Test Exception"), "Error message")
        fileLoggingTree.flush() // Wait for background write
        
        // Assert
        val logDir = File(mockFilesDir, "log")
        val logFile = logDir.listFiles()!!.first()
        val logContent = logFile.readText()
        
        assertTrue("Log content should format error priority as 'E'. Actual: $logContent", logContent.contains("E/ErrorTag"))
        assertTrue("Log content should contain error message. Actual: $logContent", logContent.contains("Error message"))
    }
}
