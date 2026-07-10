package dev.nucleusframework.composenativetray.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel
import dev.nucleusframework.core.runtime.SingleInstanceManager
import dev.nucleusframework.composenativetray.utils.allowComposeNativeTrayLogging
import dev.nucleusframework.composenativetray.utils.composeNativeTrayLoggingLevel
import dev.nucleusframework.composenativetray.utils.getTrayPosition
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import composenativetray.demo.generated.resources.icon2
import org.jetbrains.compose.resources.painterResource

/**
 * Demo application that showcases the use of the Painter API for tray icons.
 * This demo uses Res.drawable.icon and Res.drawable.icon2 resources with dynamic switching.
 */
fun main() = application {
    allowComposeNativeTrayLogging = true
    composeNativeTrayLoggingLevel = ComposeNativeTrayLoggingLevel.DEBUG

    val logTag = "PainterTrayDemo"
    
    println("$logTag: TrayPosition: ${getTrayPosition()}")

    var isWindowVisible by remember { mutableStateOf(true) }
    var alwaysShowTray by remember { mutableStateOf(true) }
    var hideOnClose by remember { mutableStateOf(true) }
    
    // Icon state for switching between two different icons
    var currentIcon by remember { mutableStateOf(Res.drawable.icon) }

    val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
        isWindowVisible = true
    })

    if (!isSingleInstance) {
        exitApplication()
        return@application
    }

    // Always create the Tray composable, but make it conditional on visibility
    val showTray = alwaysShowTray || !isWindowVisible

    if (showTray) {
        // Using the Painter API with the resource icons
        Tray(
            icon = painterResource(currentIcon),  // Using the Painter directly
            tooltip = "Painter Demo",
            primaryAction = {
                isWindowVisible = true
                println("$logTag: Primary action clicked")
            }
        ) {
            // Menu item to switch between icons
            Item(label = "Switch Icon") {
                currentIcon = if (currentIcon == Res.drawable.icon) {
                    println("$logTag: Switched to icon2")
                    Res.drawable.icon2
                } else {
                    println("$logTag: Switched to icon")
                    Res.drawable.icon
                }
            }

            Divider()

            // Standard menu items
            Item(label = "About") {
                println("$logTag: Painter API Demo - Using resource icons")
            }

            Divider()

            // Settings for tray visibility
            CheckableItem(
                label = "Always show tray",
                checked = alwaysShowTray,
                onCheckedChange = { checked ->
                    alwaysShowTray = checked
                    println("$logTag: Always show tray ${if (checked) "enabled" else "disabled"}")
                }
            )

            CheckableItem(
                label = "Hide on close",
                checked = hideOnClose,
                onCheckedChange = { checked ->
                    hideOnClose = checked
                    println("$logTag: Hide on close ${if (checked) "enabled" else "disabled"}")
                }
            )

            Divider()

            Item(label = "Hide in tray") {
                isWindowVisible = false
                println("$logTag: Application hidden in tray")
            }

            Item(label = "Exit") {
                println("$logTag: Exiting application")
                dispose()
                exitApplication()
            }
        }
    }

    Window(
        onCloseRequest = {
            if (hideOnClose) {
                isWindowVisible = false
            } else {
                exitApplication()
            }
        },
        title = "Painter Tray Demo - Resource Icons",
        visible = isWindowVisible,
        icon = painterResource(Res.drawable.icon)
    ) {
        window.toFront()
        App(
            textVisible = true,
            alwaysShowTray = alwaysShowTray,
            hideOnClose = hideOnClose
        ) { alwaysShow, hideOnCloseState ->
            alwaysShowTray = alwaysShow
            hideOnClose = hideOnCloseState
        }
    }
}