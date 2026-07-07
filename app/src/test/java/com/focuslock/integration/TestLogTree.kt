package com.focuslock.integration

import timber.log.Timber

class TestLogTree : Timber.Tree() {
    companion object {
        private val logs = mutableListOf<String>()

        fun getLogs(): List<String> = logs.toList()
        
        fun clearLogs() = logs.clear()
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        logs.add("[$tag] $message")
    }
}
