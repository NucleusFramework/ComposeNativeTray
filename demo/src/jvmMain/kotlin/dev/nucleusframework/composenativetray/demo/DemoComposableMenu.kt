package dev.nucleusframework.composenativetray.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.core.runtime.SingleInstanceManager
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import composenativetray.demo.generated.resources.icon2
import org.jetbrains.compose.resources.painterResource

/**
 * Showcases the @Composable menu DSL. No need to hoist `painterResource(...)` above
 * `application { … }` — every menu/submenu lambda is composable.
 */
fun main() = application {
    var isWindowVisible by remember { mutableStateOf(true) }
    var enableHeavyMode by remember { mutableStateOf(false) }

    val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
        isWindowVisible = true
    })
    if (!isSingleInstance) {
        exitApplication()
        return@application
    }

    Tray(
        icon = painterResource(Res.drawable.icon),
        tooltip = "Composable Menu Demo",
        primaryAction = { isWindowVisible = true },
        menuContent = {
            Item(label = "Open", icon = painterResource(Res.drawable.icon)) {
                isWindowVisible = true
            }

            SubMenu(
                label = "Advanced",
                icon = painterResource(Res.drawable.icon2),
            ) {
                Item(label = "Reload", icon = painterResource(Res.drawable.icon)) {
                    println("Reload")
                }
                Item(label = "Reset", icon = painterResource(Res.drawable.icon2)) {
                    println("Reset")
                }
            }

            Divider()

            CheckableItem(
                label = "Heavy mode",
                icon = painterResource(Res.drawable.icon2),
                checked = enableHeavyMode,
                onCheckedChange = { enableHeavyMode = it },
            )

            Divider()

            Item(label = "Exit", icon = painterResource(Res.drawable.icon2)) {
                dispose()
                exitApplication()
            }
        },
    )

    Window(
        onCloseRequest = { isWindowVisible = false },
        title = "Composable Menu Demo",
        visible = isWindowVisible,
        icon = painterResource(Res.drawable.icon),
    ) {
        window.toFront()
    }
}
