package dev.nucleusframework.composenativetray.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.nucleusframework.composenativetray.tray.api.Tray
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

/**
 * DemoTransparentWindow
 *
 * A minimal example showing a simple system tray with a transparent, undecorated window.
 * Left click (primaryAction) on the tray icon shows the window. Use the tray menu to
 * hide or exit. The window content itself is a rounded card floating on a transparent background.
 */
fun main() = application {
    var isWindowVisible by remember { mutableStateOf(true) }

    // Simple tray with primary action to show the transparent window
    Tray(
        icon = painterResource(Res.drawable.icon),
        tooltip = "Transparent Window Demo",
        primaryAction = { isWindowVisible = true }
    ) {
        Item(if (isWindowVisible) "Hide window" else "Show window") {
            isWindowVisible = !isWindowVisible
        }
        Divider()
        Item("Exit") {
            exitApplication()
        }
    }

    Window(
        onCloseRequest = { isWindowVisible = false },
        visible = isWindowVisible,
        undecorated = true,
        transparent = true,
        alwaysOnTop = false,
        resizable = false,
        title = "Transparent Window Demo",
        icon = painterResource(Res.drawable.icon)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .shadow(24.dp, shape)
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Transparent window",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "This window is undecorated and the background is transparent.",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { isWindowVisible = false }) {
                    Text("Hide")
                }
            }
        }
    }
}
