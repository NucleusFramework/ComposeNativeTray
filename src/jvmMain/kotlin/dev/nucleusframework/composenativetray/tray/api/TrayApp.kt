@file:OptIn(
    ExperimentalTrayAppApi::class,
    ExperimentalTransitionApi::class,
    InternalAnimationApi::class,
)

package dev.nucleusframework.composenativetray.tray.api

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.InternalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.nucleusframework.composenativetray.lib.linux.LinuxOutsideClickWatcher
import dev.nucleusframework.composenativetray.lib.mac.MacNativeBridge
import dev.nucleusframework.composenativetray.lib.mac.MacOSWindowManager
import dev.nucleusframework.composenativetray.lib.mac.MacOutsideClickWatcher
import dev.nucleusframework.composenativetray.lib.windows.WindowsOutsideClickWatcher
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.tray.impl.WindowsTrayInitializer
import dev.nucleusframework.composenativetray.utils.ComposableIconUtils
import dev.nucleusframework.composenativetray.utils.IconRenderProperties
import dev.nucleusframework.composenativetray.utils.MenuContentHash
import dev.nucleusframework.composenativetray.utils.PersistentAnimatedVisibility
import dev.nucleusframework.composenativetray.utils.WindowVisibilityMonitor
import dev.nucleusframework.composenativetray.utils.debugln
import dev.nucleusframework.composenativetray.utils.getTrayWindowPositionForInstance
import dev.nucleusframework.composenativetray.utils.isMenuBarInDarkMode
import dev.nucleusframework.core.runtime.LinuxDesktopEnvironment
import dev.nucleusframework.core.runtime.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.EventQueue.invokeLater
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.util.concurrent.atomic.AtomicLong

// --------------------- Public API (defaults) ---------------------

private val defaultTrayAppEnterTransition =
    if (Platform.Current == Platform.Windows) {
        slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(250, easing = EaseInOut),
        ) + fadeIn(animationSpec = tween(200, easing = EaseInOut))
    } else {
        fadeIn(
            animationSpec =
                tween(
                    if (LinuxDesktopEnvironment.Current == LinuxDesktopEnvironment.KDE) 50 else 200,
                    easing = EaseInOut,
                ),
        )
    }

private val defaultTrayAppExitTransition =
    if (Platform.Current == Platform.Windows) {
        slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(250, easing = EaseInOut),
        ) + fadeOut(animationSpec = tween(200, easing = EaseInOut))
    } else {
        fadeOut(
            animationSpec =
                tween(
                    if (LinuxDesktopEnvironment.Current == LinuxDesktopEnvironment.KDE) 50 else 200,
                    easing = EaseInOut,
                ),
        )
    }

private val defaultVerticalOffset =
    when (Platform.Current) {
        Platform.Windows -> -10
        Platform.MacOS -> 5
        else ->
            when (LinuxDesktopEnvironment.Current) {
                LinuxDesktopEnvironment.Gnome -> 10
                else -> 0
            }
    }

// --------------------- Public API (overloads) ---------------------

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    val iconContent: @Composable () -> Unit = {
        val isDark = isMenuBarInDarkMode()
        Image(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter =
                tint?.let { ColorFilter.tint(it) }
                    ?: if (isDark) ColorFilter.tint(Color.White) else ColorFilter.tint(Color.Black),
        )
    }
    TrayApp(
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        windowSize = windowSize,
        visibleOnStart = visibleOnStart,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        horizontalOffset = horizontalOffset,
        verticalOffset = verticalOffset,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        menu = menu,
        content = content,
    )
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: Painter,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    val iconContent: @Composable () -> Unit = {
        Image(painter = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
    TrayApp(
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        windowSize = windowSize,
        visibleOnStart = visibleOnStart,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        horizontalOffset = horizontalOffset,
        verticalOffset = verticalOffset,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        menu = menu,
        content = content,
    )
}

/** Painter on Windows, ImageVector on macOS/Linux */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    windowsIcon: Painter,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    if (Platform.Current == Platform.Windows) {
        TrayApp(
            icon = windowsIcon,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
            undecorated = undecorated,
            resizable = resizable,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            menu = menu,
            content = content,
        )
    } else {
        TrayApp(
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
            undecorated = undecorated,
            resizable = resizable,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            menu = menu,
            content = content,
        )
    }
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: DrawableResource,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    TrayApp(
        icon = painterResource(icon),
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        windowSize = windowSize,
        visibleOnStart = visibleOnStart,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        horizontalOffset = horizontalOffset,
        verticalOffset = verticalOffset,
        menu = menu,
        content = content,
    )
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    windowsIcon: DrawableResource,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    if (Platform.Current == Platform.Windows) {
        TrayApp(
            icon = painterResource(windowsIcon),
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
            undecorated = undecorated,
            resizable = resizable,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            menu = menu,
            content = content,
        )
    } else {
        TrayApp(
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
            undecorated = undecorated,
            resizable = resizable,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            menu = menu,
            content = content,
        )
    }
}

// --------------------- Core router ---------------------

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    when (Platform.Current) {
        Platform.Linux ->
            TrayAppImplLinux(
                iconContent,
                iconRenderProperties,
                tooltip,
                state,
                windowSize,
                visibleOnStart,
                enterTransition,
                exitTransition,
                transparent,
                windowsTitle,
                windowIcon,
                undecorated,
                resizable,
                horizontalOffset,
                verticalOffset,
                onPreviewKeyEvent,
                onKeyEvent,
                menu,
                content,
            )

        else ->
            TrayAppImplOriginal(
                iconContent,
                iconRenderProperties,
                tooltip,
                state,
                windowSize,
                visibleOnStart,
                enterTransition,
                exitTransition,
                transparent,
                windowsTitle,
                windowIcon,
                undecorated,
                resizable,
                horizontalOffset,
                verticalOffset,
                onPreviewKeyEvent,
                onKeyEvent,
                menu,
                content,
            )
    }
}

// --------------------- Impl: macOS/Windows ---------------------

@Composable
private fun ApplicationScope.TrayAppImplOriginal(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties,
    tooltip: String,
    state: TrayAppState?,
    windowSize: DpSize?,
    visibleOnStart: Boolean,
    enterTransition: EnterTransition,
    exitTransition: ExitTransition,
    transparent: Boolean,
    windowsTitle: String,
    windowIcon: Painter?,
    undecorated: Boolean,
    resizable: Boolean,
    horizontalOffset: Int,
    verticalOffset: Int,
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    menu: (TrayMenuBuilder.() -> Unit)?,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    val trayAppState =
        state ?: rememberTrayAppState(
            initialWindowSize = windowSize ?: DpSize(300.dp, 200.dp),
            initiallyVisible = visibleOnStart,
            initialDismissMode = TrayWindowDismissMode.AUTO,
        )
    val isVisible by trayAppState.isVisible.collectAsState()
    val currentWindowSize by trayAppState.windowSize.collectAsState()
    val dismissMode by trayAppState.dismissMode.collectAsState()

    val tray = remember { NativeTray() }
    val isDark = isMenuBarInDarkMode()
    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) + isDark.hashCode()
    val pngIconPath =
        remember(contentHash) {
            ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)
        }
    val windowsIconPath =
        remember(contentHash) {
            if (Platform.Current == Platform.Windows) {
                ComposableIconUtils.renderComposableToIcoFile(
                    iconRenderProperties,
                    iconContent,
                )
            } else {
                pngIconPath
            }
        }
    val menuHash = MenuContentHash.calculateMenuHash(menu)

    var shouldShowWindow by remember { mutableStateOf(false) }

    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 150L

    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 200L
    val minHiddenDurationMs = 100L

    var lastFocusLostAt by remember { mutableStateOf(0L) }
    var autoHideEnabledAt by remember { mutableStateOf(0L) }

    // Position pre-computed at click time so the LaunchedEffect can use it immediately.
    var pendingPosition by remember { mutableStateOf<WindowPosition?>(null) }

    // Store window reference for macOS Space detection
    var windowRef by remember { mutableStateOf<java.awt.Window?>(null) }

    val dialogState = rememberDialogState(size = currentWindowSize)
    LaunchedEffect(currentWindowSize) { dialogState.size = currentWindowSize }

    // Visibility controller for exit-finish observation; content will NOT be disposed.
    val visibleState = remember { MutableTransitionState(false) }

    // Thread-safe timestamps for cross-thread communication (IO thread reads, EDT writes)
    val lastFocusLostAtMs = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val lastHiddenAtMs = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val lastShownAtMs = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val lastPrimaryActionAtMs = remember { java.util.concurrent.atomic.AtomicLong(0L) }

    val requestHideExplicit: () -> Unit = {
        val now = System.currentTimeMillis()
        val sinceShow = now - lastShownAtMs.get()
        debugln {
            "[TrayApp] requestHideExplicit called, sinceShow=${sinceShow}ms, " +
                "thread=${Thread.currentThread().name}"
        }
        if (sinceShow >= minVisibleDurationMs) {
            trayAppState.hide()
            lastHiddenAtMs.set(System.currentTimeMillis())
            lastHiddenAt = lastHiddenAtMs.get()
        } else {
            val wait = minVisibleDurationMs - sinceShow
            CoroutineScope(Dispatchers.IO).launch {
                delay(wait)
                trayAppState.hide()
                lastHiddenAtMs.set(System.currentTimeMillis())
                lastHiddenAt = lastHiddenAtMs.get()
            }
        }
    }

    val internalPrimaryAction: () -> Unit = {
        val now = System.currentTimeMillis()
        // Read directly from StateFlow for thread-safe cross-thread access
        val isVisibleNow = trayAppState.isVisible.value
        val timeSinceLastAction = now - lastPrimaryActionAtMs.get()
        debugln {
            "[TrayApp] primaryAction: isVisibleNow=$isVisibleNow, " +
                "isVisible(compose)=$isVisible, " +
                "timeSinceLastAction=${timeSinceLastAction}ms, " +
                "thread=${Thread.currentThread().name}"
        }
        if (timeSinceLastAction >= toggleDebounceMs) {
            lastPrimaryActionAtMs.set(now)
            lastPrimaryActionAt = now
            if (isVisibleNow) {
                // On macOS, check if the window is on another Space
                if (Platform.Current == Platform.MacOS) {
                    val onActiveSpace =
                        runCatching {
                            MacOSWindowManager().isFloatingWindowOnActiveSpace()
                        }.getOrElse { true }
                    debugln { "[TrayApp] primaryAction: onActiveSpace=$onActiveSpace" }
                    if (!onActiveSpace) {
                        // Window is on another Space → move it to current Space via native API
                        debugln { "[TrayApp] primaryAction -> MOVE TO CURRENT SPACE" }
                        invokeLater {
                            runCatching { MacOSWindowManager().bringFloatingWindowToFront() }
                        }
                    } else {
                        debugln { "[TrayApp] primaryAction -> HIDE" }
                        requestHideExplicit()
                    }
                } else {
                    debugln { "[TrayApp] primaryAction -> HIDE" }
                    requestHideExplicit()
                }
            } else {
                val hiddenAgo = now - lastHiddenAtMs.get()
                val focusLostAgo = now - lastFocusLostAtMs.get()
                debugln {
                    "[TrayApp] primaryAction SHOW path: " +
                        "hiddenAgo=${hiddenAgo}ms, focusLostAgo=${focusLostAgo}ms"
                }
                if (hiddenAgo >= minHiddenDurationMs) {
                    if ((Platform.Current == Platform.Windows || Platform.Current == Platform.MacOS) && focusLostAgo < 150) {
                        // ignore immediate re-show after focus loss on Windows/macOS
                        debugln { "[TrayApp] primaryAction -> SHOW BLOCKED (recent focus loss)" }
                    } else {
                        debugln { "[TrayApp] primaryAction -> SHOW" }
                        // Pre-compute position at click time: the native status item
                        // geometry is guaranteed to be available right now.
                        runCatching {
                            val widthPx = currentWindowSize.width.value.toInt()
                            val heightPx = currentWindowSize.height.value.toInt()
                            pendingPosition =
                                getTrayWindowPositionForInstance(
                                    tray.instanceKey(), widthPx, heightPx, horizontalOffset, verticalOffset,
                                )
                        }
                        trayAppState.show()
                    }
                } else {
                    debugln {
                        "[TrayApp] primaryAction -> SHOW BLOCKED " +
                            "(too soon after hide: ${hiddenAgo}ms < ${minHiddenDurationMs}ms)"
                    }
                }
            }
        } else {
            debugln { "[TrayApp] primaryAction -> DEBOUNCED (${timeSinceLastAction}ms < ${toggleDebounceMs}ms)" }
        }
    }

    LaunchedEffect(isVisible) {
        // Drive transition state
        visibleState.targetState = isVisible

        if (isVisible) {
            if (!shouldShowWindow) {
                val preComputed = pendingPosition
                pendingPosition = null

                val position =
                    if (preComputed != null && preComputed !is WindowPosition.PlatformDefault) {
                        debugln { "[TrayApp] Using preComputed position: $preComputed" }
                        preComputed
                    } else {
                        // Fallback: poll for position (e.g. initiallyVisible or programmatic show)
                        // Wait for Windows to finish reorganizing tray icons after adding a new one.
                        // Windows moves icons around after creation, so we need to wait and re-poll.
                        debugln { "[TrayApp] No preComputed position, waiting for tray to stabilize..." }
                        delay(400) // Give Windows time to reorganize tray icons

                        val widthPx = currentWindowSize.width.value.toInt()
                        val heightPx = currentWindowSize.height.value.toInt()

                        // On Windows, force a fresh position capture via the native API
                        if (Platform.Current == Platform.Windows) {
                            debugln { "[TrayApp] Re-capturing tray position from native API..." }
                            WindowsTrayInitializer.refreshPosition(tray.instanceKey())
                            delay(50) // Let the position update propagate
                        }

                        var pos: WindowPosition = WindowPosition.PlatformDefault
                        val deadline = System.currentTimeMillis() + 3000
                        while (pos is WindowPosition.PlatformDefault && System.currentTimeMillis() < deadline) {
                            pos =
                                getTrayWindowPositionForInstance(
                                    tray.instanceKey(), widthPx, heightPx, horizontalOffset, verticalOffset,
                                )
                            debugln { "[TrayApp] Polled position: $pos" }
                            if (pos is WindowPosition.PlatformDefault) delay(250)
                        }
                        pos
                    }
                debugln { "[TrayApp] Setting dialogState.position = $position" }
                dialogState.position = position

                // Wait for Compose to apply the position before showing the window
                delay(30)

                if (Platform.Current == Platform.Windows) {
                    autoHideEnabledAt = System.currentTimeMillis() + 1000
                }
                debugln { "[TrayApp] Now showing window" }
                shouldShowWindow = true
                val showTime = System.currentTimeMillis()
                lastShownAt = showTime
                lastShownAtMs.set(showTime)
            }
        } else {
            // Wait for exit animation to finish, then actually hide the window
            if (shouldShowWindow) {
                snapshotFlow { visibleState.isIdle && !visibleState.currentState }.first { it }
                shouldShowWindow = false
                val hideTime = System.currentTimeMillis()
                lastHiddenAt = hideTime
                lastHiddenAtMs.set(hideTime)
            }
        }
    }

    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    LaunchedEffect(Unit) {
        if (Platform.Current == Platform.MacOS) {
            WindowVisibilityMonitor.hasAnyVisibleWindows.collectLatest { hasVisible ->
                runCatching {
                    val manager = MacOSWindowManager()
                    if (hasVisible) manager.showInDock() else manager.hideFromDock()
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { tray.dispose() } }

    DialogWindow(
        onCloseRequest = { requestHideExplicit() },
        title = windowsTitle,
        icon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        focusable = true,
        alwaysOnTop = true,
        transparent = transparent,
        visible = shouldShowWindow,
        state = dialogState,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        DisposableEffect(shouldShowWindow, dismissMode) {
            if (!shouldShowWindow) return@DisposableEffect onDispose { }

            // Store window reference for Space detection on macOS
            windowRef = window

            try {
                window.name = WindowVisibilityMonitor.TRAY_DIALOG_NAME
            } catch (_: Throwable) {
            }
            runCatching { WindowVisibilityMonitor.recompute() }

            debugln {
                "[TrayApp] Window shown at native position: x=${window.x}, y=${window.y}, " +
                    "dialogState.position=${dialogState.position}"
            }

            invokeLater {
                // Move the popup to the current Space before bringing it to front (macOS)
                if (Platform.Current == Platform.MacOS) {
                    debugln { "[TrayApp] Setting up macOS Space behavior on window..." }
                    val nativeResult = runCatching { MacNativeBridge.nativeSetMoveToActiveSpace() }
                    debugln {
                        "[TrayApp] nativeSetMoveToActiveSpace: " +
                            "${nativeResult.exceptionOrNull()?.message ?: "OK"}"
                    }
                    val moveResult = runCatching { MacOSWindowManager().setMoveToActiveSpace(window) }
                    debugln {
                        "[TrayApp] setMoveToActiveSpace " +
                            "result=${moveResult.getOrNull()}, " +
                            "error=${moveResult.exceptionOrNull()?.message}"
                    }
                }
                debugln { "[TrayApp] After invokeLater: window at x=${window.x}, y=${window.y}" }
                runCatching {
                    window.toFront()
                    window.requestFocus()
                    window.requestFocusInWindow()
                }
            }

            val focusListener =
                object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) = Unit

                    override fun windowLostFocus(e: WindowEvent?) {
                        val now = System.currentTimeMillis()
                        lastFocusLostAtMs.set(now)
                        lastFocusLostAt = now
                        debugln {
                            "[TrayApp] windowLostFocus at $now, " +
                                "dismissMode=$dismissMode, " +
                                "thread=${Thread.currentThread().name}"
                        }
                        if (Platform.Current == Platform.Windows && now < autoHideEnabledAt) return
                        // On macOS, don't auto-hide if the window is not on the active Space.
                        // A Space switch caused the focus loss — let the primary action handle it.
                        if (Platform.Current == Platform.MacOS) {
                            val onActiveSpace =
                                runCatching {
                                    MacOSWindowManager().isFloatingWindowOnActiveSpace()
                                }.getOrElse { true }
                            debugln { "[TrayApp] windowLostFocus: onActiveSpace=$onActiveSpace" }
                            if (!onActiveSpace) return
                        }
                        if (dismissMode == TrayWindowDismissMode.AUTO) requestHideExplicit()
                    }
                }

            val macWatcher =
                if (dismissMode == TrayWindowDismissMode.AUTO && Platform.Current == Platform.MacOS) {
                    MacOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { requestHideExplicit() } },
                    ).also { it.start() }
                } else {
                    null
                }

            val linuxWatcher =
                if (dismissMode == TrayWindowDismissMode.AUTO && Platform.Current == Platform.Linux) {
                    LinuxOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { requestHideExplicit() } },
                    ).also { it.start() }
                } else {
                    null
                }

            val windowsWatcher =
                if (dismissMode == TrayWindowDismissMode.AUTO && Platform.Current == Platform.Windows) {
                    WindowsOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { requestHideExplicit() } },
                    ).also { it.start() }
                } else {
                    null
                }

            window.addWindowFocusListener(focusListener)
            onDispose {
                window.removeWindowFocusListener(focusListener)
                macWatcher?.stop()
                linuxWatcher?.stop()
                windowsWatcher?.stop()
                runCatching { WindowVisibilityMonitor.recompute() }
                windowRef = null
            }
        }

        // ---- Persistent (non-disposing) visibility wrapper ----
        PersistentAnimatedVisibility(
            visibleState = visibleState,
            enter = enterTransition,
            exit = exitTransition,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer()
                        // Child can still opt into its own per-node enter/exit if desired:
                        .animateEnterExit(),
            ) { content() }
        }
    }
}

// --------------------- Impl: Linux ---------------------

@Composable
private fun ApplicationScope.TrayAppImplLinux(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties,
    tooltip: String,
    state: TrayAppState?,
    windowSize: DpSize?,
    visibleOnStart: Boolean,
    enterTransition: EnterTransition,
    exitTransition: ExitTransition,
    transparent: Boolean,
    windowsTitle: String,
    windowIcon: Painter?,
    undecorated: Boolean,
    resizable: Boolean,
    horizontalOffset: Int,
    verticalOffset: Int,
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    menu: (TrayMenuBuilder.() -> Unit)?,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    val trayAppState =
        state ?: rememberTrayAppState(
            initialWindowSize = windowSize ?: DpSize(300.dp, 200.dp),
            initiallyVisible = visibleOnStart,
            initialDismissMode = TrayWindowDismissMode.AUTO,
        )
    val isVisible by trayAppState.isVisible.collectAsState()
    val currentWindowSize by trayAppState.windowSize.collectAsState()
    val dismissMode by trayAppState.dismissMode.collectAsState()

    val tray = remember { NativeTray() }
    val instanceKey = remember { tray.instanceKey() }

    val isDark = isMenuBarInDarkMode()
    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) + isDark.hashCode()
    val pngIconPath =
        remember(contentHash) { ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent) }
    val windowsIconPath = pngIconPath
    val menuHash = MenuContentHash.calculateMenuHash(menu)

    var shouldShowWindow by remember { mutableStateOf(false) }
    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L
    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 350L
    val minHiddenDurationMs = 250L

    val initialPositionForFirstFrame =
        remember(instanceKey, currentWindowSize, horizontalOffset, verticalOffset) {
            val w = currentWindowSize.width.value.toInt()
            val h = currentWindowSize.height.value.toInt()
            getTrayWindowPositionForInstance(instanceKey, w, h, horizontalOffset, verticalOffset)
        }

    val dialogState = rememberDialogState(position = initialPositionForFirstFrame, size = currentWindowSize)
    LaunchedEffect(currentWindowSize) { dialogState.size = currentWindowSize }

    // Visibility controller for exit-finish detection; content will NOT be disposed.
    val visibleState = remember { MutableTransitionState(false) }

    val requestHideExplicit: () -> Unit = {
        val now = System.currentTimeMillis()
        val sinceShow = now - lastShownAt
        if (sinceShow >= minVisibleDurationMs) {
            trayAppState.hide()
            lastHiddenAt = now
        } else {
            val wait = (minVisibleDurationMs - sinceShow).coerceAtLeast(0)
            CoroutineScope(Dispatchers.IO).launch {
                delay(wait)
                trayAppState.hide()
                lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    val internalPrimaryAction: () -> Unit = action@{
        val now = System.currentTimeMillis()
        if (now - lastPrimaryActionAt < toggleDebounceMs) return@action
        lastPrimaryActionAt = now
        if (isVisible) {
            requestHideExplicit()
        } else if (now - lastHiddenAt >= minHiddenDurationMs) {
            val w = currentWindowSize.width.value.toInt()
            val h = currentWindowSize.height.value.toInt()
            dialogState.position = getTrayWindowPositionForInstance(instanceKey, w, h, horizontalOffset, verticalOffset)
            trayAppState.show()
        }
    }

    LaunchedEffect(isVisible) {
        // Drive transition state
        visibleState.targetState = isVisible

        if (isVisible) {
            if (!shouldShowWindow) {
                shouldShowWindow = true
                lastShownAt = System.currentTimeMillis()
            }
        } else {
            if (shouldShowWindow) {
                snapshotFlow { visibleState.isIdle && !visibleState.currentState }.first { it }
                shouldShowWindow = false
                lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    // Re-anchor when visible and size/offset changes
    LaunchedEffect(currentWindowSize, horizontalOffset, verticalOffset, shouldShowWindow) {
        if (shouldShowWindow) {
            val w = currentWindowSize.width.value.toInt()
            val h = currentWindowSize.height.value.toInt()
            dialogState.position = getTrayWindowPositionForInstance(instanceKey, w, h, horizontalOffset, verticalOffset)
        }
    }

    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    DisposableEffect(Unit) { onDispose { tray.dispose() } }

    DialogWindow(
        onCloseRequest = { requestHideExplicit() },
        title = windowsTitle,
        icon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        focusable = shouldShowWindow,
        alwaysOnTop = true,
        transparent = transparent,
        visible = shouldShowWindow,
        state = dialogState,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        DisposableEffect(shouldShowWindow, dismissMode) {
            if (!shouldShowWindow) return@DisposableEffect onDispose { }

            runCatching {
                val w = currentWindowSize.width.value.toInt()
                val h = currentWindowSize.height.value.toInt()
                dialogState.position =
                    getTrayWindowPositionForInstance(instanceKey, w, h, horizontalOffset, verticalOffset)
            }

            val linuxWatcher =
                if (dismissMode == TrayWindowDismissMode.AUTO) {
                    LinuxOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { requestHideExplicit() } },
                    ).also { it.start() }
                } else {
                    null
                }

            window.addWindowFocusListener(
                object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) = Unit

                    override fun windowLostFocus(e: WindowEvent?) {
                        if (dismissMode == TrayWindowDismissMode.AUTO) requestHideExplicit()
                    }
                },
            )

            onDispose { linuxWatcher?.stop() }
        }

        // ---- Persistent (non-disposing) visibility wrapper ----
        PersistentAnimatedVisibility(
            visibleState = visibleState,
            enter = enterTransition,
            exit = exitTransition,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer()
                        .animateEnterExit(),
            ) { content() }
        }
    }
}
