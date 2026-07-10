package dev.nucleusframework.composenativetray.utils

import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel.Companion.DEBUG
import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel.Companion.ERROR
import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel.Companion.INFO
import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel.Companion.VERBOSE
import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel.Companion.WARN
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

var allowComposeNativeTrayLogging: Boolean = false

@Deprecated("migrate to composeNativeTrayLoggingLevel", ReplaceWith("composeNativeTrayLoggingLevel"))
var composeNativeTrayloggingLevel by ::composeNativeTrayLoggingLevel

var composeNativeTrayLoggingLevel: ComposeNativeTrayLoggingLevel = VERBOSE

class ComposeNativeTrayLoggingLevel private constructor(
    private val priority: Int,
) : Comparable<ComposeNativeTrayLoggingLevel> {
    override fun compareTo(other: ComposeNativeTrayLoggingLevel): Int {
        return priority.compareTo(other.priority)
    }

    companion object {
        @JvmField val VERBOSE = ComposeNativeTrayLoggingLevel(0)

        @JvmField val DEBUG = ComposeNativeTrayLoggingLevel(1)

        @JvmField val INFO = ComposeNativeTrayLoggingLevel(2)

        @JvmField val WARN = ComposeNativeTrayLoggingLevel(3)

        @JvmField val ERROR = ComposeNativeTrayLoggingLevel(4)
    }
}

private const val COLOR_RED = "\u001b[31m"
private const val COLOR_AQUA = "\u001b[36m"
private const val COLOR_LIGHT_GRAY = "\u001b[37m"
private const val COLOR_ORANGE = "\u001b[38;2;255;165;0m"
private const val COLOR_RESET = "\u001b[0m"

// Time formatter
private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

private fun getCurrentTimestamp(): String {
    return LocalDateTime.now().format(timeFormatter)
}

internal fun debugln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayLoggingLevel <= DEBUG) {
        println("[${getCurrentTimestamp()}] ${message()}")
    }
}

internal fun verboseln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayLoggingLevel <= VERBOSE) {
        println("[${getCurrentTimestamp()}] ${message()}", COLOR_LIGHT_GRAY)
    }
}

internal fun infoln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayLoggingLevel <= INFO) {
        println("[${getCurrentTimestamp()}] ${message()}", COLOR_AQUA)
    }
}

internal fun warnln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayLoggingLevel <= WARN) {
        println("[${getCurrentTimestamp()}] ${message()}", COLOR_ORANGE)
    }
}

internal fun errorln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayLoggingLevel <= ERROR) {
        println("[${getCurrentTimestamp()}] ${message()}", COLOR_RED)
    }
}

private fun println(
    message: String,
    color: String,
) {
    println(color + message + COLOR_RESET)
}
