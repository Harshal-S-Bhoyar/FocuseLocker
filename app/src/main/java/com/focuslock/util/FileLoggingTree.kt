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

// Direct Logcat fallback is intentional here: Timber would recurse through this Timber tree on failure.
@android.annotation.SuppressLint("LogNotTimber")
class FileLoggingTree(
    private val context: Context,
    @Volatile var minLogLevel: Int = Log.INFO,
    private val maxDaysToKeep: Int = 7
) : Timber.DebugTree() {

    private val logExecutor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    // ponytail: buffer log lines and flush in one open-write-close per interval.
    // Ceiling: up to 5s of logs lost on hard kill (not crash — flush() covers that).
    private val logBuffer = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val flushLock = Any()
    private val flushScheduler = Executors.newSingleThreadScheduledExecutor()

    init {
        // Clean up old logs on initialization
        logExecutor.execute {
            cleanupOldLogs()
        }
        flushScheduler.scheduleAtFixedRate({ flushBuffer() }, 5, 5, TimeUnit.SECONDS)
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= minLogLevel
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!isLoggable(tag, priority)) return
        super.log(priority, tag, message, t) // Also log to Logcat

        val now = Date()
        val currentTime = timeFormat.format(now)
        val threadName = Thread.currentThread().name

        // ponytail: array lookup over when-block — same result, 1 line.
        val priorityStr = "??VDIWEA".getOrElse(priority) { '?' }.toString()

        val logLine = "$currentTime [$threadName] $priorityStr/${tag ?: "FocusLock"}: $message\n"

        logBuffer.offer(logLine)
    }

    private fun flushBuffer() {
        // Drain inside the lock: an explicit flush() must wait for a scheduled
        // batch already in progress instead of racing it on the same file.
        synchronized(flushLock) {
            val lines = mutableListOf<String>()
            while (true) {
                lines.add(logBuffer.poll() ?: break)
            }
            if (lines.isEmpty()) return

            try {
                val logDir = File(context.getExternalFilesDir(null), "log")
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                val currentDate = dateFormat.format(Date())
                val logFile = File(logDir, "app_logs_$currentDate.txt")

                FileWriter(logFile, true).use { writer ->
                    lines.forEach { writer.append(it) }
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
            logExecutor.submit { flushBuffer() }.get(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e("FileLoggingTree", "Error flushing logs", e)
        }
    }
}
