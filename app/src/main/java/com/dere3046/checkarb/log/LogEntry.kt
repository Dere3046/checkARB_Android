package com.dere3046.checkarb.log

import java.util.Date

data class LogEntry(
    val timestamp: Date = Date(),
    val level: Level,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }
}