package dev.nucleusframework.composenativetray.demo

import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.graalvm.GraalVmInitializer
import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel
import dev.nucleusframework.core.runtime.SingleInstanceManager
import dev.nucleusframework.composenativetray.utils.allowComposeNativeTrayLogging
import dev.nucleusframework.composenativetray.utils.composeNativeTrayLoggingLevel
import dev.nucleusframework.composenativetray.utils.getTrayPosition
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import composenativetray.demo.generated.resources.icon2
import org.jetbrains.compose.resources.painterResource
import java.awt.Color
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer

private enum class ServiceStatus {
    RUNNING, STOPPED
}

fun main() {
    GraalVmInitializer.initialize()
    application {
    val logTag = "NativeTray"
    allowComposeNativeTrayLogging = true
    composeNativeTrayLoggingLevel = ComposeNativeTrayLoggingLevel.DEBUG

    println("$logTag: TrayPosition: ${getTrayPosition()}")

    var isWindowVisible by remember { mutableStateOf(true) }
    var textVisible by remember { mutableStateOf(false) }
    var alwaysShowTray by remember { mutableStateOf(true) }
    var hideOnClose by remember { mutableStateOf(true) }
    var serviceStatus by remember { mutableStateOf(ServiceStatus.STOPPED) }

    val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
        isWindowVisible = true
    })

    if (!isSingleInstance) {
        exitApplication()
        return@application
    }

    val running = serviceStatus == ServiceStatus.RUNNING
    var icon by remember { mutableStateOf(Res.drawable.icon) }

    // Always create the Tray composable, but make it conditional on visibility
    // This ensures it's recomposed when alwaysShowTray changes
    val showTray = alwaysShowTray || !isWindowVisible
    var isVisible by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("Change Item Name") }

    if (showTray) {
        Tray(
            iconContent = {
                Image(
                    painter = painterResource(icon),
                    contentDescription = "Application Icon",
                    // Use alwaysShowTray as a key to force recomposition when it changes
                )
            },
            primaryAction = {
                isWindowVisible = true
                println("$logTag: On Primary Clicked")
            },
            tooltip = "My Application",
            // Pass isVisible and name as menuKey to force recomposition when they change
            menuContent = {
                Item("Change icon") {
                    icon = if (icon == Res.drawable.icon) Res.drawable.icon2 else Res.drawable.icon
                }
                if (isVisible) {
                    Item("Hide Me") {
                        isVisible = false
                    }
                }
                Item(name) {
                    name = "I've changed !"
                }
                // Dynamic Service Menu
                SubMenu(label = "Service Control") {
                    Item(label = "Start Service", isEnabled = !running) {
                        println("$logTag: Start Service selected")
                        serviceStatus = ServiceStatus.RUNNING
                    }
                    Item(label = "Stop Service", isEnabled = running) {
                        println("$logTag: Stop Service selected")
                        serviceStatus = ServiceStatus.STOPPED
                    }
                    Item(label = "Service Status: ${if (running) "Running" else "Stopped"}", isEnabled = false)
                }

                Divider()

                // Options SubMenu
                SubMenu(label = "Options") {
                    Item(label = "Show Text") {
                        println("$logTag: Show Text selected")
                        textVisible = true
                    }
                    Item(label = "Hide Text") {
                        println("$logTag: Hide Text selected")
                        textVisible = false
                    }
                }

                Divider()

                Item(label = "About") {
                    println("$logTag: Application v1.0 - Developed by Elyahou")
                }

                Divider()

                CheckableItem(
                    label = "Always show tray",
                    checked = alwaysShowTray,
                    onCheckedChange = { isChecked ->
                        alwaysShowTray = isChecked
                        println("$logTag: Always show tray ${if (isChecked) "enabled" else "disabled"}")
                    }
                )

                CheckableItem(
                    label = "Hide on close",
                    checked = hideOnClose,
                    onCheckedChange = { isChecked ->
                        hideOnClose = isChecked
                        println("$logTag: Hide on close ${if (isChecked) "enabled" else "disabled"}")
                    }
                )

                Divider()

                Item(label = "Exit", isEnabled = true) {
                    println("$logTag: Exiting the application")
                    dispose()
                    exitApplication()
                }
            }
        )
    }

    Window(
        onCloseRequest = {
            if (hideOnClose) {
                isWindowVisible = false
            } else {
                exitApplication()
            }
        },
        title = "Compose Desktop Application with Two Screens",
        visible = isWindowVisible,
        icon = painterResource(Res.drawable.icon) // Optional: Set window icon
    ) {
        window.toFront()
        App(textVisible, alwaysShowTray, hideOnClose) { alwaysShow, hideOnCloseState ->
            alwaysShowTray = alwaysShow
            hideOnClose = hideOnCloseState
        }
    }
}
}