package dev.nucleusframework.composenativetray.demo

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel
import dev.nucleusframework.composenativetray.utils.allowComposeNativeTrayLogging
import dev.nucleusframework.composenativetray.utils.composeNativeTrayLoggingLevel
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode
import dev.nucleusframework.window.NucleusDecoratedWindowTheme
import dev.nucleusframework.window.TitleBar
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import composenativetray.demo.generated.resources.icon2

/**
 * Demo application that showcases using DrawableResource directly for the Tray icon
 * and for context menu icons, e.g.:
 *   Tray(icon = Res.drawable.icon) { Item(label = "Settings", icon = Res.drawable.icon2) }
 */
fun main() = nucleusApplication(enableSingleInstance = false) {
    allowComposeNativeTrayLogging = true
    composeNativeTrayLoggingLevel = ComposeNativeTrayLoggingLevel.DEBUG

    val logTag = "DemoWithDrawableResources"

    var isWindowVisible by remember { mutableStateOf(true) }
    var alwaysShowTray by remember { mutableStateOf(true) }
    var hideOnClose by remember { mutableStateOf(true) }

    val showTray = alwaysShowTray || !isWindowVisible

    if (showTray) {
        // Using the DrawableResource overload directly
        Tray(
            icon = Res.drawable.icon,
            tooltip = "Demo: DrawableResource icons",
            primaryAction = {
                isWindowVisible = true
                println("$logTag: Primary action clicked")
            }
        ) {
            SubMenu(
                label = "Menu with icons",
                icon = Res.drawable.icon2
            ) {
                Item(label = "Action 1", icon = Res.drawable.icon) {
                    println("$logTag: Action 1 selected")
                }
                Item(label = "Action 2", icon = Res.drawable.icon2) {
                    println("$logTag: Action 2 selected")
                }
            }

            Divider()

            CheckableItem(
                label = "Always show tray",
                icon = Res.drawable.icon,
                checked = alwaysShowTray,
                onCheckedChange = { checked ->
                    alwaysShowTray = checked
                    println("$logTag: Always show tray ${if (checked) "enabled" else "disabled"}")
                }
            )

            CheckableItem(
                label = "Hide on close",
                icon = Res.drawable.icon2,
                checked = hideOnClose,
                onCheckedChange = { checked ->
                    hideOnClose = checked
                    println("$logTag: Hide on close ${if (checked) "enabled" else "disabled"}")
                }
            )

            Divider()

            Item(label = "Exit", icon = Res.drawable.icon2) {
                println("$logTag: Exiting application")
                dispose()
                exitApplication()
            }
        }
    }

    NucleusDecoratedWindowTheme(isDark = isSystemInDarkMode()) {
        DecoratedWindow(
            onCloseRequest = {
                if (hideOnClose) {
                    isWindowVisible = false
                } else {
                    exitApplication()
                }
            },
            title = "Demo with DrawableResource icons",
            visible = isWindowVisible,
            // Note: Window icon can still use painterResource if desired; omitted here for simplicity
        ) {
            TitleBar { Text("Demo with DrawableResource icons") }
            val window = nucleusWindow
            LaunchedEffect(isWindowVisible) {
                if (isWindowVisible) {
                    window.toFront()
                }
            }
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
}
