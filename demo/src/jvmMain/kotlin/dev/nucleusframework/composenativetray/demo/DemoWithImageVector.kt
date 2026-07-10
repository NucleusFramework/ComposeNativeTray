package dev.nucleusframework.composenativetray.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.nucleusframework.composenativetray.demo.svg.AcademicCap
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel
import dev.nucleusframework.core.runtime.SingleInstanceManager
import dev.nucleusframework.composenativetray.utils.allowComposeNativeTrayLogging
import dev.nucleusframework.composenativetray.utils.composeNativeTrayLoggingLevel
import dev.nucleusframework.composenativetray.utils.getTrayPosition
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

/**
 * Demo application that showcases the use of the ImageVector API for tray icons.
 * This demo uses the AcademicCap vector as the tray icon.
 */
fun main() = application {
    allowComposeNativeTrayLogging = false
    composeNativeTrayLoggingLevel = ComposeNativeTrayLoggingLevel.DEBUG

    val logTag = "ImageVectorTrayDemo"
    
    println("$logTag: TrayPosition: ${getTrayPosition()}")

    var isWindowVisible by remember { mutableStateOf(true) }
    var alwaysShowTray by remember { mutableStateOf(true) }
    var hideOnClose by remember { mutableStateOf(true) }
    
    // Tint color state for the icon
    var iconTint by remember { mutableStateOf<Color?>(null) } // null means use default (white/black based on theme)

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
        // Using the ImageVector API with the AcademicCap vector
        Tray(
            icon = AcademicCap,  // Using the ImageVector directly
            tint = iconTint,     // Using the tint parameter (null means auto-adapt to theme)
            tooltip = "Academic Cap Demo",
            primaryAction = {
                isWindowVisible = true
                println("$logTag: Primary action clicked")
            }
        ) {
            // Menu items to demonstrate changing the tint color
            SubMenu(label = "Icon Color") {
                Item(label = "Default (Auto)") {
                    iconTint = null
                    println("$logTag: Icon color set to default (auto)")
                }
                Item(label = "Red") {
                    iconTint = Color.Red
                    println("$logTag: Icon color set to red")
                }
                Item(label = "Green") {
                    iconTint = Color.Green
                    println("$logTag: Icon color set to green")
                }
                Item(label = "Blue") {
                    iconTint = Color.Blue
                    println("$logTag: Icon color set to blue")
                }
                Item(label = "Yellow") {
                    iconTint = Color.Yellow
                    println("$logTag: Icon color set to yellow")
                }
            }

            Divider()

            // Standard menu items
            Item(label = "About") {
                println("$logTag: ImageVector API Demo - Using AcademicCap vector")
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
        title = "ImageVector Tray Demo - AcademicCap",
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