package com.dere3046.checkarb.log

import android.content.Context
import android.net.Uri
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object LogManager {
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private var bufferSize = 500
    private var enabled = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun setBufferSize(size: Int) {
        bufferSize = size.coerceIn(50, 3000)
        trimToSize()
    }

    fun getBufferSize(): Int = bufferSize

    fun setEnabled(enable: Boolean) {
        enabled = enable
        if (!enable) clear()
    }

    fun isEnabled(): Boolean = enabled

    private fun trimToSize() {
        while (logs.size > bufferSize) {
            logs.removeAt(0)
        }
    }

    fun v(tag: String, msg: String) {
        if (enabled) add(LogEntry(level = LogEntry.Level.VERBOSE, tag = tag, message = msg))
    }

    fun d(tag: String, msg: String) {
        if (enabled) add(LogEntry(level = LogEntry.Level.DEBUG, tag = tag, message = msg))
    }

    fun i(tag: String, msg: String) {
        if (enabled) add(LogEntry(level = LogEntry.Level.INFO, tag = tag, message = msg))
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (enabled) add(LogEntry(level = LogEntry.Level.WARN, tag = tag, message = msg, throwable = tr))
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        // Always log errors regardless of enabled flag
        add(LogEntry(level = LogEntry.Level.ERROR, tag = tag, message = msg, throwable = tr))
    }

    private fun add(entry: LogEntry) {
        logs.add(entry)
        trimToSize()
    }

    fun getAll(): List<LogEntry> = logs.toList()

    fun clear() = logs.clear()

    suspend fun exportToFile(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                PrintWriter(out).use { writer ->
                    logs.forEach { entry ->
                        val time = dateFormat.format(entry.timestamp)
                        val level = entry.level.name
                        val tag = entry.tag
                        val msg = entry.message
                        writer.println("$time $level/$tag: $msg")
                        entry.throwable?.let {
                            val sw = StringWriter()
                            it.printStackTrace(PrintWriter(sw))
                            writer.println(sw.toString())
                        }
                    }
                }
            } != null
        } catch (e: Exception) {
            e("LogManager", "Failed to export logs", e)
            false
        }
    }
}