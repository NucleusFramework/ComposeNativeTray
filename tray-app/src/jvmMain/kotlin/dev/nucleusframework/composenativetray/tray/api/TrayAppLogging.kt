package dev.nucleusframework.composenativetray.tray.api

import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel
import dev.nucleusframework.composenativetray.utils.allowComposeNativeTrayLogging
import dev.nucleusframework.composenativetray.utils.composeNativeTrayLoggingLevel

// Local logging for the tray-app module. Mirrors the core gate (which stays module-private)
// off the shared, public `composeNativeTrayLoggingLevel` / `allowComposeNativeTrayLogging` config.

internal fun debugln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayLoggingLevel <= ComposeNativeTrayLoggingLevel.DEBUG) {
        println("[${java.time.LocalDateTime.now()}] ${message()}")
    }
}

internal fun errorln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayLoggingLevel <= ComposeNativeTrayLoggingLevel.ERROR) {
        println("[31m[${java.time.LocalDateTime.now()}] ${message()}[0m")
    }
}
