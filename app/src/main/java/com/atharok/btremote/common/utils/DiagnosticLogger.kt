package com.atharok.btremote.common.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.concurrent.Executors

/**
 * App-owned diagnostic log. Android third-party apps cannot read the global Logcat buffer,
 * so this records the app's own structured events to an app-specific directory.
 */
object DiagnosticLogger {
    private const val TAG = "BtRemoteDiagnostics"
    private const val MAX_LOG_BYTES = 2L * 1024L * 1024L
    private const val LOG_FILE_NAME = "btremote-debug.log"

    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "btremote-log-writer").apply { isDaemon = true }
    }
    private val fileLock = Any()
    @Volatile private var logFile: File? = null

    fun initialize(context: Context) {
        if (logFile != null) return
        synchronized(this) {
            if (logFile != null) return
            val baseDirectory = context.getExternalFilesDir(null) ?: context.filesDir
            val directory = File(baseDirectory, "logs")
            if (!directory.exists() && !directory.mkdirs()) {
                Log.e(TAG, "Unable to create diagnostic log directory")
                return
            }
            logFile = File(directory, LOG_FILE_NAME)
            info("logger.initialized", "path=app-specific/logs/$LOG_FILE_NAME")
        }
    }

    fun info(event: String, details: String = "") = write("INFO", event, details)
    fun warn(event: String, details: String = "", throwable: Throwable? = null) =
        write("WARN", event, details, throwable)
    fun error(event: String, details: String = "", throwable: Throwable? = null) =
        write("ERROR", event, details, throwable)

    /** Writes synchronously so a fatal exception is recorded before process termination. */
    fun recordFatal(throwable: Throwable) {
        val line = formatLine("FATAL", "app.uncaught_exception", "", throwable)
        synchronized(fileLock) {
            val file = logFile ?: return
            try {
                rotateIfNeeded(file, line.toByteArray(Charsets.UTF_8).size.toLong())
                file.appendText(line, Charsets.UTF_8)
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to persist fatal diagnostic", exception)
            }
        }
    }

    private fun write(
        level: String,
        event: String,
        details: String,
        throwable: Throwable? = null
    ) {
        val line = formatLine(level, event, details, throwable)
        when (level) {
            "ERROR" -> Log.e(TAG, "$event $details", throwable)
            "WARN" -> Log.w(TAG, "$event $details", throwable)
            else -> Log.i(TAG, "$event $details")
        }
        val file = logFile ?: return
        try {
            ioExecutor.execute {
                synchronized(fileLock) {
                    try {
                        rotateIfNeeded(file, line.toByteArray(Charsets.UTF_8).size.toLong())
                        file.appendText(line, Charsets.UTF_8)
                    } catch (exception: Exception) {
                        Log.e(TAG, "Unable to append diagnostic log", exception)
                    }
                }
            }
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Diagnostic writer is unavailable", exception)
        }
    }

    private fun formatLine(
        level: String,
        event: String,
        details: String,
        throwable: Throwable?
    ): String {
        val safeDetails = details.replace('\n', ' ').replace('\r', ' ')
        val stackTrace = throwable?.let {
            StringWriter().also { writer -> it.printStackTrace(PrintWriter(writer)) }.toString()
        }?.replace("\r", "")?.let { "\n$it" } ?: ""
        return "${Instant.now()} $level [$event] $safeDetails$stackTrace\n"
    }

    private fun rotateIfNeeded(file: File, incomingBytes: Long) {
        if (!file.exists() || file.length() + incomingBytes <= MAX_LOG_BYTES) return
        val previous = File(file.parentFile, "$LOG_FILE_NAME.1")
        if (previous.exists()) previous.delete()
        if (!file.renameTo(previous)) {
            file.writeText("", Charsets.UTF_8)
        }
    }
}
