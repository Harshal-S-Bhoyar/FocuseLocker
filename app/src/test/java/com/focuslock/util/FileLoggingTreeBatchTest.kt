package com.focuslock.util

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.File

class FileLoggingTreeBatchTest {

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
        val tempDir = System.getProperty("java.io.tmpdir")
        mockFilesDir = File(tempDir, "focuslock_batch_test_logs")
        mockFilesDir.mkdirs()
        every { mockContext.getExternalFilesDir(null) } returns mockFilesDir

        fileLoggingTree = FileLoggingTree(mockContext)
        Timber.plant(fileLoggingTree)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        mockFilesDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `many logs then one flush lands every line in a single file`() {
        repeat(25) { i -> Timber.tag("BatchTag").i("batched message $i") }
        fileLoggingTree.flush()

        val logDir = File(mockFilesDir, "log")
        val files = logDir.listFiles() ?: emptyArray()
        assertEquals("Batched writes must land in exactly one file", 1, files.size)
        // Exact line match: a substring check would let "batched message 1"
        // pass on the "batched message 10" line.
        val lines = files.first().readText().lines()
        repeat(25) { i ->
            assertTrue(
                "Missing exact line for message $i",
                lines.any { it.endsWith("I/BatchTag: batched message $i") }
            )
        }
        assertEquals("No partial or extra lines", 25, lines.filter { it.isNotBlank() }.size)
    }

    @Test
    fun `flush with empty buffer writes nothing`() {
        fileLoggingTree.flush()
        val logDir = File(mockFilesDir, "log")
        val files = logDir.listFiles() ?: emptyArray()
        assertTrue("Empty buffer must not create files", files.isEmpty())
    }
}
