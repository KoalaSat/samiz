package com.koalasat.samiz.model
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object Logger {
    private val _logMessages = MutableLiveData<StringBuilder>().apply { value = StringBuilder() }
    val logMessages: LiveData<StringBuilder> get() = _logMessages

    fun d(
        tag: String,
        message: String,
    ) {
        Log.d(tag, message) // Log to Logcat
        appendLog("INFO", "$tag: $message", LogLevel.INFO)
    }

    fun e(
        tag: String,
        message: String,
    ) {
        Log.e(tag, message) // Log to Logcat
        appendLog("ERROR", "$tag: $message", LogLevel.ERROR)
    }

    private fun appendLog(
        level: String,
        message: String,
        logLevel: LogLevel,
    ) {
        val currentLogs = _logMessages.value ?: StringBuilder()
        currentLogs.append("$level: $message\n")
        _logMessages.postValue(currentLogs)
    }

    enum class LogLevel {
        INFO,
        ERROR,
    }
}
