package com.focuslock.util

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLoggingTree(private val context: Context) : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t) // Also log to Logcat

        try {
            val logDir = File(context.getExternalFilesDir(null), "log")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
            val currentDate = dateFormat.format(Date())
            val fileName = "app_logs_$currentDate.txt"
            val logFile = File(logDir, fileName)

            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val currentTime = timeFormat.format(Date())

            val priorityStr = when (priority) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "?"
            }

            val threadName = Thread.currentThread().name
            
            val logLine = "$currentTime [$threadName] $priorityStr/${tag ?: "FocusLock"}: $message\n"
            
            FileWriter(logFile, true).use { writer ->
                writer.append(logLine)
            }
        } catch (e: Exception) {
            // Cannot log this exception to Timber because it would cause an infinite loop
            Log.e("FileLoggingTree", "Error while writing log to file", e)
        }
    }
}
