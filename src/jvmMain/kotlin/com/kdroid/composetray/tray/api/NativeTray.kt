@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.kdroid.composetray.tray.api

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.tray.impl.AwtTrayInitializer
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import com.kdroid.composetray.tray.impl.MacTrayInitializer
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.utils.ComposableIconUtils
import com.kdroid.composetray.utils.IconRenderProperties
import com.kdroid.composetray.utils.MenuContentHash
import com.kdroid.composetray.utils.debugln
import com.kdroid.composetray.utils.errorln
import com.kdroid.composetray.utils.extractToTempIfDifferent
import com.kdroid.composetray.utils.isMenuBarInDarkMode
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.platformtools.OperatingSystem.LINUX
import io.github.kdroidfilter.platformtools.OperatingSystem.MACOS
import io.github.kdroidfilter.platformtools.OperatingSystem.UNKNOWN
import io.github.kdroidfilter.platformtools.OperatingSystem.WINDOWS
import io.github.kdroidfilter.platformtools.getOperatingSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.internal.LowPriorityInOverloadResolution

internal class NativeTray {
    private val trayScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val awtTrayUsed = AtomicBoolean(false)

    private val os = getOperatingSystem()
    private val instanceId: String = "tray-" + System.identityHashCode(this)
    private var initialized = false

    // Expose the unique instance key so UI code (TrayApp) can compute per-instance positions
    fun instanceKey(): String = instanceId

    fun update(
        iconPath: String,
        windowsIconPath: String = iconPath,
        tooltip: String,
        primaryAction: (() -> Unit)?,
        menuContent: (TrayMenuBuilder.() -> Unit)?,
        onMenuOpened: (() -> Unit)? = null,
    ) {
        if (!initialized) {
            initializeTray(iconPath, windowsIconPath, tooltip, primaryAction, menuContent)
            initialized = true
            return
        }

        try {
            when (os) {
                LINUX ->
                    LinuxTrayInitializer.update(
                        instanceId,
                        iconPath,
                        tooltip,
                        primaryAction,
                        menuContent,
                        onMenuOpened,
                    )
                WINDOWS ->
                    WindowsTrayInitializer.update(
                        instanceId,
                        windowsIconPath,
                        tooltip,
                        primaryAction,
                        menuContent,
                        onMenuOpened,
                    )
                MACOS ->
                    MacTrayInitializer.update(
                        instanceId,
                        iconPath,
                        tooltip,
                        primaryAction,
                        menuContent,
                        onMenuOpened,
                    )
                UNKNOWN -> {
                    AwtTrayInitializer.update(iconPath, tooltip, primaryAction, menuContent)
                    awtTrayUsed.set(true)
                }
                else -> {}
            }
        } catch (th: Throwable) {
            errorln { "[NativeTray] Error updating tray: $th" }
        }
    }

    /**
     * New update path: render the composable icon to PNG/ICO with retries and then update/init the tray.
     * If rendering keeps failing, we log and **do not create/update** the tray (never crash the app).
     *
     * @param lightIconContent Optional composable for the light-appearance icon (macOS only).
     * @param darkIconContent Optional composable for the dark-appearance icon (macOS only).
     */
    fun updateComposable(
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties = IconRenderProperties(),
        tooltip: String,
        primaryAction: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null,
        maxAttempts: Int = 3,
        backoffMs: Long = 200,
        lightIconContent: (@Composable () -> Unit)? = null,
        darkIconContent: (@Composable () -> Unit)? = null,
        onMenuOpened: (() -> Unit)? = null,
    ) {
        trayScope.launch {
            val rendered = renderIconsWithRetry(iconContent, iconRenderProperties, maxAttempts, backoffMs)
            if (rendered == null) {
                errorln {
                    "[NativeTray] Icon rendering failed after $maxAttempts attempts. " +
                        "Tray will not be created/updated."
                }
                return@launch
            }

            val (pngIconPath, windowsIconPath) = rendered

            if (!initialized) {
                initializeTray(pngIconPath, windowsIconPath, tooltip, primaryAction, menuContent, onMenuOpened)
                initialized = true
            } else {
                try {
                    when (os) {
                        LINUX ->
                            LinuxTrayInitializer.update(
                                instanceId,
                                pngIconPath,
                                tooltip,
                                primaryAction,
                                menuContent,
                                onMenuOpened,
                            )
                        WINDOWS ->
                            WindowsTrayInitializer.update(
                                instanceId,
                                windowsIconPath,
                                tooltip,
                                primaryAction,
                                menuContent,
                                onMenuOpened,
                            )
                        MACOS ->
                            MacTrayInitializer.update(
                                instanceId,
                                pngIconPath,
                                tooltip,
                                primaryAction,
                                menuContent,
                                onMenuOpened,
                            )
                        UNKNOWN -> {
                            AwtTrayInitializer.update(pngIconPath, tooltip, primaryAction, menuContent)
                            awtTrayUsed.set(true)
                        }
                        else -> {}
                    }
                } catch (th: Throwable) {
                    errorln { "[NativeTray] Error updating tray after successful render: $th" }
                }
            }

            // On macOS, pre-render light/dark variants for instant appearance switching
            if (os == MACOS && lightIconContent != null && darkIconContent != null) {
                try {
                    val lightPath =
                        ComposableIconUtils.renderComposableToPngFile(
                            iconRenderProperties,
                            lightIconContent,
                        )
                    val darkPath = ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, darkIconContent)
                    MacTrayInitializer.setAppearanceIcons(instanceId, lightPath, darkPath)
                } catch (th: Throwable) {
                    errorln { "[NativeTray] Failed to render appearance icons: $th" }
                }
            }
        }
    }

    /**
     * Set macOS appearance icons directly from file paths.
     */
    fun setMacOSAppearanceIcons(
        lightPath: String,
        darkPath: String,
    ) {
        if (os == MACOS && initialized) {
            MacTrayInitializer.setAppearanceIcons(instanceId, lightPath, darkPath)
        }
    }

    private suspend fun renderIconsWithRetry(
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        maxAttempts: Int,
        backoffMs: Long,
    ): Pair<String, String>? {
        var attempt = 0
        while (attempt < maxAttempts) {
            try {
                // Render the composable to PNG for general platforms
                val pngIconPath = ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)

                // On Windows, also render to ICO; on other OSes reuse PNG path
                val windowsIconPath =
                    if (os == WINDOWS) {
                        ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)
                    } else {
                        pngIconPath
                    }

                debugln {
                    "[NativeTray] Rendered tray icons (attempt ${attempt + 1}/$maxAttempts): " +
                        "PNG=$pngIconPath, WIN=$windowsIconPath"
                }
                return pngIconPath to windowsIconPath
            } catch (e: Throwable) {
                errorln {
                    "[NativeTray] Icon render attempt ${attempt + 1} failed: " +
                        "${e.message ?: e::class.simpleName}"
                }
                attempt++
                if (attempt < maxAttempts) delay(backoffMs)
            }
        }
        return null
    }

    fun dispose() {
        when (os) {
            LINUX -> LinuxTrayInitializer.dispose(instanceId)
            WINDOWS -> WindowsTrayInitializer.dispose(instanceId)
            MACOS -> MacTrayInitializer.dispose(instanceId)
            UNKNOWN -> if (awtTrayUsed.get()) AwtTrayInitializer.dispose()
            else -> {}
        }
        initialized = false
    }

    /**
     * Constructor that accepts file paths for icons
     * @deprecated Use the Composable-based update path instead
     */
    @Deprecated(
        message = "Use the constructor with composable icon content instead",
        replaceWith = ReplaceWith("NativeTray(iconContent, tooltip, primaryAction, menuContent)"),
    )
    private fun initializeTray(
        iconPath: String,
        windowsIconPath: String = iconPath,
        tooltip: String = "",
        primaryAction: (() -> Unit)?,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null,
        onMenuOpened: (() -> Unit)? = null,
    ) {
        trayScope.launch {
            var trayInitialized = false
            val os = getOperatingSystem()
            try {
                when (os) {
                    LINUX -> {
                        debugln { "[NativeTray] Initializing Linux tray with icon path: $iconPath" }
                        LinuxTrayInitializer.initialize(
                            instanceId,
                            iconPath,
                            tooltip,
                            primaryAction,
                            menuContent,
                            onMenuOpened,
                        )
                        trayInitialized = true
                    }
                    WINDOWS -> {
                        debugln { "[NativeTray] Initializing Windows tray with icon path: $windowsIconPath" }
                        WindowsTrayInitializer.initialize(
                            instanceId,
                            windowsIconPath,
                            tooltip,
                            primaryAction,
                            menuContent,
                            onMenuOpened,
                        )
                        trayInitialized = true
                    }
                    MACOS -> {
                        debugln { "[NativeTray] Initializing macOS tray with icon path: $iconPath" }
                        MacTrayInitializer.initialize(
                            instanceId,
                            iconPath,
                            tooltip,
                            primaryAction,
                            menuContent,
                            onMenuOpened,
                        )
                        trayInitialized = true
                    }
                    else -> {}
                }
            } catch (th: Throwable) {
                errorln { "[NativeTray] Error initializing tray: $th" }
            }

            val awtTrayRequired = os == UNKNOWN || !trayInitialized
            if (awtTrayRequired) {
                if (AwtTrayInitializer.isSupported()) {
                    try {
                        debugln { "[NativeTray] Initializing AWT tray with icon path: $iconPath" }
                        AwtTrayInitializer.initialize(iconPath, tooltip, primaryAction, menuContent)
                        awtTrayUsed.set(true)
                    } catch (th: Throwable) {
                        errorln { "[NativeTray] Error initializing AWT tray: $th" }
                    }
                } else {
                    debugln { "[NativeTray] AWT tray is not supported" }
                }
            }
        }
    }

    /**
     * Constructor that accepts a Composable for the icon — kept for backward compatibility.
     * Now delegates to the retry-safe render+init/update path.
     */
    private fun initializeTray(
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties = IconRenderProperties(),
        tooltip: String = "",
        primaryAction: (() -> Unit)?,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null,
        onMenuOpened: (() -> Unit)? = null,
    ) {
        updateComposable(
            iconContent = iconContent,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            primaryAction = primaryAction,
            menuContent = menuContent,
            onMenuOpened = onMenuOpened,
        )
    }
}

/**
 * Composable helpers
 */
@Deprecated(
    message = "Use the version with composable icon content instead",
    replaceWith = ReplaceWith("Tray(iconContent, tooltip, primaryAction, menuContent)"),
)
@Composable
fun ApplicationScope.Tray(
    iconPath: String,
    windowsIconPath: String = iconPath,
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    onMenuOpened: (() -> Unit)? = null,
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    val absoluteIconPath = remember(iconPath) { extractToTempIfDifferent(iconPath)?.absolutePath.orEmpty() }
    val absoluteWindowsIconPath =
        remember(iconPath, windowsIconPath) {
            if (windowsIconPath == iconPath) {
                absoluteIconPath
            } else {
                extractToTempIfDifferent(windowsIconPath)?.absolutePath.orEmpty()
            }
        }

    val tray = remember { NativeTray() }

    // Calculate menu hash to detect changes
    val menuHash = MenuContentHash.calculateMenuHash(menuContent)

    // Update when params change, including menuHash
    LaunchedEffect(absoluteIconPath, absoluteWindowsIconPath, tooltip, primaryAction, menuContent, menuHash) {
        tray.update(absoluteIconPath, absoluteWindowsIconPath, tooltip, primaryAction, menuContent, onMenuOpened)
    }

    // Dispose only when Tray is removed from composition
    DisposableEffect(Unit) {
        onDispose {
            debugln { "[NativeTray] onDispose" }
            tray.dispose()
        }
    }
}

@Composable
@LowPriorityInOverloadResolution
fun ApplicationScope.Tray(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    onMenuOpened: (() -> Unit)? = null,
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    val isDark = isMenuBarInDarkMode() // Observe menu bar theme to trigger recomposition on changes

    // Calculate a hash of the rendered composable content to detect changes, including theme state
    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) + isDark.hashCode()

    // Calculate a hash of the menu content to detect changes
    val menuHash = MenuContentHash.calculateMenuHash(menuContent)

    val tray = remember { NativeTray() }

    // On any content/menu change, delegate to retry-safe path
    LaunchedEffect(contentHash, tooltip, primaryAction, menuContent, menuHash) {
        tray.updateComposable(
            iconContent = iconContent,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            primaryAction = primaryAction,
            menuContent = menuContent,
            maxAttempts = 3,
            backoffMs = 200,
            onMenuOpened = onMenuOpened,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            debugln { "[NativeTray] onDispose" }
            tray.dispose()
        }
    }
}

@Composable
@LowPriorityInOverloadResolution
fun ApplicationScope.Tray(
    icon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    onMenuOpened: (() -> Unit)? = null,
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    val isDark = isMenuBarInDarkMode()
    val isSystemInDarkTheme = isSystemInDarkMode()
    val isMacOS = getOperatingSystem() == MACOS

    // Define the icon content lambda
    val iconContent: @Composable () -> Unit = {
        Image(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter =
                tint?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) }
                    ?: if (isDark) {
                        androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                    } else {
                        androidx.compose.ui.graphics.ColorFilter.tint(Color.Black)
                    },
        )
    }

    // On macOS with auto-tint, pre-render both light and dark variants for instant switching
    val lightIconContent: (@Composable () -> Unit)? =
        if (tint == null && isMacOS) {
            {
                Image(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black),
                )
            }
        } else {
            null
        }

    val darkIconContent: (@Composable () -> Unit)? =
        if (tint == null && isMacOS) {
            {
                Image(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                )
            }
        } else {
            null
        }

    // Calculate menu hash to detect changes
    val menuHash = MenuContentHash.calculateMenuHash(menuContent)

    // Updated contentHash to include icon and tint for proper recomposition on changes
    val contentHash =
        ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) +
            isDark.hashCode() +
            isSystemInDarkTheme.hashCode() +
            icon.hashCode() +
            (tint?.hashCode() ?: 0)

    val tray = remember { NativeTray() }

    LaunchedEffect(contentHash, tooltip, primaryAction, menuContent, menuHash) {
        tray.updateComposable(
            iconContent = iconContent,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            primaryAction = primaryAction,
            menuContent = menuContent,
            maxAttempts = 3,
            backoffMs = 200,
            lightIconContent = lightIconContent,
            darkIconContent = darkIconContent,
            onMenuOpened = onMenuOpened,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            debugln { "[NativeTray] onDispose" }
            tray.dispose()
        }
    }
}

@Composable
@LowPriorityInOverloadResolution
fun ApplicationScope.Tray(
    icon: Painter,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    onMenuOpened: (() -> Unit)? = null,
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    val isDark = isMenuBarInDarkMode() // Included for consistency, even if not used in rendering

    // Define the icon content lambda
    val iconContent: @Composable () -> Unit = {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Calculate menu hash to detect changes
    val menuHash = MenuContentHash.calculateMenuHash(menuContent)

    // Updated contentHash to include icon for proper recomposition on changes
    val contentHash =
        ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) +
            isDark.hashCode() +
            icon.hashCode()

    val tray = remember { NativeTray() }

    LaunchedEffect(contentHash, tooltip, primaryAction, menuContent, menuHash) {
        tray.updateComposable(
            iconContent = iconContent,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            primaryAction = primaryAction,
            menuContent = menuContent,
            maxAttempts = 3,
            backoffMs = 200,
            onMenuOpened = onMenuOpened,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            debugln { "[NativeTray] onDispose" }
            tray.dispose()
        }
    }
}

/**
 * Platform-polymorphic helper
 */
@Composable
@LowPriorityInOverloadResolution
fun ApplicationScope.Tray(
    windowsIcon: Painter,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    onMenuOpened: (() -> Unit)? = null,
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    val os = getOperatingSystem()

    if (os == WINDOWS) {
        // Use Painter for Windows
        Tray(
            icon = windowsIcon,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            primaryAction = primaryAction,
            onMenuOpened = onMenuOpened,
            menuContent = menuContent,
        )
    } else {
        // Use ImageVector for macOS and Linux
        Tray(
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            primaryAction = primaryAction,
            onMenuOpened = onMenuOpened,
            menuContent = menuContent,
        )
    }
}

/**
 * DrawableResource helpers
 */
@Composable
@LowPriorityInOverloadResolution
fun ApplicationScope.Tray(
    icon: DrawableResource,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    onMenuOpened: (() -> Unit)? = null,
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    // Convert DrawableResource to Painter and delegate to the Painter overload
    val painter = painterResource(icon)
    Tray(
        icon = painter,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        primaryAction = primaryAction,
        onMenuOpened = onMenuOpened,
        menuContent = menuContent,
    )
}

@Composable
@LowPriorityInOverloadResolution
fun ApplicationScope.Tray(
    windowsIcon: DrawableResource,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    onMenuOpened: (() -> Unit)? = null,
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    val os = getOperatingSystem()

    if (os == WINDOWS) {
        // Convert DrawableResource to Painter for Windows and delegate
        val painter = painterResource(windowsIcon)
        Tray(
            icon = painter,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            primaryAction = primaryAction,
            onMenuOpened = onMenuOpened,
            menuContent = menuContent,
        )
    } else {
        // Use ImageVector for macOS and Linux
        Tray(
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            primaryAction = primaryAction,
            onMenuOpened = onMenuOpened,
            menuContent = menuContent,
        )
    }
}
