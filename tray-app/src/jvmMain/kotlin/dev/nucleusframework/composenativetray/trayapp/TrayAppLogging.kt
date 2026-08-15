package dev.nucleusframework.composenativetray.trayapp

import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel
import dev.nucleusframework.composenativetray.utils.allowComposeNativeTrayLogging
import dev.nucleusframework.composenativetray.utils.composeNativeTrayLoggingLevel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Local logging for the tray-app module. Mirrors the core gate (which stays module-private)
// off the shared, public `composeNativeTrayLoggingLevel` / `allowComposeNativeTrayLogging` config,
// and matches core's timestamp format so interleaved stdout lines stay uniform.

private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

private fun timestamp(): String = LocalDateTime.now().format(timeFormatter)

internal fun debugln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayLoggingLevel <= ComposeNativeTrayLoggingLevel.DEBUG) {
        println("[${timestamp()}] ${message()}")
    }
}

internal fun errorln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayLoggingLevel <= ComposeNativeTrayLoggingLevel.ERROR) {
        println("[31m[${timestamp()}] ${message()}[0m")
    }
}
