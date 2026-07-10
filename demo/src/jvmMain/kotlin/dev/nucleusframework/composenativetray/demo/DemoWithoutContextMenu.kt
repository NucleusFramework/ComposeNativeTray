package dev.nucleusframework.composenativetray.demo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.composenativetray.utils.ComposeNativeTrayLoggingLevel
import dev.nucleusframework.composenativetray.utils.SingleInstanceManager
import dev.nucleusframework.composenativetray.utils.allowComposeNativeTrayLogging
import dev.nucleusframework.composenativetray.utils.composeNativeTrayLoggingLevel
import dev.nucleusframework.composenativetray.utils.getTrayPosition
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon

fun main() = application {
    allowComposeNativeTrayLogging = true
    composeNativeTrayLoggingLevel = ComposeNativeTrayLoggingLevel.DEBUG

    val logTag = "NativeTray"
    
    println("$logTag: TrayPosition: ${getTrayPosition()}")

    var isWindowVisible by remember { mutableStateOf(true) }
    var textVisible by remember { mutableStateOf(false) }
    var alwaysShowTray by remember { mutableStateOf(false) }
    var hideOnClose by remember { mutableStateOf(true) }

    val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
        isWindowVisible = true
    })

    if (!isSingleInstance) {
        exitApplication()
        return@application
    }

    // Always create the Tray composable, but make it conditional on visibility
    // This ensures it's recomposed when alwaysShowTray changes
    val showTray = alwaysShowTray || !isWindowVisible

    if (showTray) {
        Tray(
            iconContent = {
                // Use alwaysShowTray as a key to force recomposition when it changes
                val alpha = if (alwaysShowTray) 0.5f else 0.5f
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(300.dp)).background(Color.Red.copy(alpha = alpha)))
            },
            primaryAction = {
                isWindowVisible = true
                println("$logTag: On Primary Clicked")
            },
            tooltip = "My Application"
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
        icon = org.jetbrains.compose.resources.painterResource(Res.drawable.icon) // Optional: Set window icon
    ) {
        App(textVisible, alwaysShowTray, hideOnClose) { alwaysShow, hideOnCloseState ->
            alwaysShowTray = alwaysShow
            hideOnClose = hideOnCloseState
        }
    }
}