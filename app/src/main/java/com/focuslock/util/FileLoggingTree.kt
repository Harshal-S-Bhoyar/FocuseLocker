package com.focuslock.util

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FileLoggingTree(
    private val context: Context,
    @Volatile var minLogLevel: Int = Log.INFO,
    private val maxDaysToKeep: Int = 7
) : Timber.DebugTree() {

    private val logExecutor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    init {
        // Clean up old logs on initialization
        logExecutor.execute {
            cleanupOldLogs()
        }
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= minLogLevel
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t) // Also log to Logcat
        
        if (!isLoggable(tag, priority)) return

        val currentTime = timeFormat.format(Date())
        val currentDate = dateFormat.format(Date())
        val threadName = Thread.currentThread().name

        val priorityStr = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }

        val logLine = "$currentTime [$threadName] $priorityStr/${tag ?: "FocusLock"}: $message\n"

        logExecutor.execute {
            try {
                val logDir = File(context.getExternalFilesDir(null), "log")
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                val fileName = "app_logs_$currentDate.txt"
                val logFile = File(logDir, fileName)

                FileWriter(logFile, true).use { writer ->
                    writer.append(logLine)
                }
            } catch (e: Exception) {
                // Cannot log this exception to Timber because it would cause an infinite loop
                Log.e("FileLoggingTree", "Error while writing log to file", e)
            }
        }
    }

    /**
     * Delete log files older than maxDaysToKeep
     */
    private fun cleanupOldLogs() {
        try {
            val logDir = File(context.getExternalFilesDir(null), "log")
            if (!logDir.exists()) return

            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxDaysToKeep.toLong())
            
            logDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("app_logs_") && file.name.endsWith(".txt")) {
                    if (file.lastModified() < cutoffTime) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileLoggingTree", "Error cleaning up old logs", e)
        }
    }
    
    /**
     * Flush all pending logs to disk. Useful for crash reporting before terminating.
     */
    fun flush() {
        try {
            logExecutor.submit { /* Empty task to wait for the queue to flush */ }.get(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e("FileLoggingTree", "Error flushing logs", e)
        }
    }
}
