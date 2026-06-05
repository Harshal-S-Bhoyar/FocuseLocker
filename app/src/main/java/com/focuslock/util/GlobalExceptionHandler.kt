package com.focuslock.util

import timber.log.Timber

class GlobalExceptionHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Log the fatal exception using Timber.
        // Because Timber is configured with FileLoggingTree, this will be written to the log file.
        Timber.e(throwable, "FATAL UNCAUGHT EXCEPTION in thread ${thread.name}")

        // Allow some time for the file writer to finish writing before the process is killed
        try {
            Thread.sleep(200)
        } catch (e: InterruptedException) {
            // Ignored
        }

        // Delegate back to the system's default exception handler to let the app crash naturally
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
