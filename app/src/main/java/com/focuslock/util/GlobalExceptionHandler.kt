package com.focuslock.util

import timber.log.Timber

class GlobalExceptionHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Log the fatal exception using Timber.
        // Because Timber is configured with FileLoggingTree, this will be written to the log file.
        Timber.e(throwable, "FATAL UNCAUGHT EXCEPTION in thread ${thread.name}")

        // Flush any background file writes before process is killed
        Timber.forest().forEach { tree ->
            if (tree is FileLoggingTree) {
                tree.flush()
            }
        }

        // Delegate back to the system's default exception handler to let the app crash naturally
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
