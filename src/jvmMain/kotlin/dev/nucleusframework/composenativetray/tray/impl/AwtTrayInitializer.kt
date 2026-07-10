package dev.nucleusframework.composenativetray.tray.impl

import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.menu.impl.AwtTrayMenuBuilderImpl
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

object AwtTrayInitializer {
    // Stores the reference to the current TrayIcon
    private var trayIcon: TrayIcon? = null

    fun isSupported(): Boolean = SystemTray.isSupported()

    /**
     * Initializes the system tray with the specified parameters.
     *
     * @param iconPath Path to the tray icon.
     * @param tooltip Tooltip text for the icon.
     * @param onLeftClick Action to execute on left-click on the icon.
     * @param menuContent Content of the tray menu.
     * @throws IllegalStateException If the system does not support the tray.
     */
    fun initialize(
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)?,
        menuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        if (!isSupported()) {
            throw IllegalStateException("System tray is not supported.")
        }

        // If a trayIcon already exists, remove it before creating a new one
        dispose()

        val systemTray = SystemTray.getSystemTray()
        val popupMenu = PopupMenu()

        // Create the tray icon
        val trayImage = Toolkit.getDefaultToolkit().getImage(iconPath)

        val newTrayIcon =
            TrayIcon(trayImage, tooltip, popupMenu).apply {
                isImageAutoSize = true

                // On macOS, we cannot easily override the default left-click behavior
                // when a popup menu is attached. We have two options:
                // 1. Accept that left-click opens the menu (macOS convention)
                // 2. Use a more complex native implementation via JNA

                if (getOperatingSystem() == OperatingSystem.MACOS) {
                    // For macOS, add primary action as the first menu item if both menu and action exist
                    if (onLeftClick != null && menuContent != null) {
                        // The primary action will be added as the first menu item
                        // This is handled in the menu building phase
                    }
                } else {
                    // For other platforms, handle left-click normally
                    onLeftClick?.let { clickAction ->
                        addMouseListener(
                            object : MouseAdapter() {
                                override fun mouseClicked(e: MouseEvent) {
                                    if (e.button == MouseEvent.BUTTON1) {
                                        clickAction()
                                    }
                                }
                            },
                        )
                    }
                }
            }

        // Add the menu content if specified
        menuContent?.let {
            // For macOS, prepend the primary action to the menu if it exists
            if (getOperatingSystem() == OperatingSystem.MACOS && onLeftClick != null) {
                val menuBuilder = AwtTrayMenuBuilderImpl(popupMenu, newTrayIcon)
                menuBuilder.Item("Open", true) { onLeftClick.invoke() }
                menuBuilder.Divider()
                menuBuilder.apply(it)
            } else {
                AwtTrayMenuBuilderImpl(popupMenu, newTrayIcon).apply(it)
            }
        }

        // Add the icon to the system tray
        systemTray.add(newTrayIcon)

        // Store the reference for future use
        trayIcon = newTrayIcon
    }

    fun update(
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)?,
        menuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        dispose()
        initialize(iconPath, tooltip, onLeftClick, menuContent)
    }

    /**
     * Disposes of the current tray icon, if it exists.
     */
    fun dispose() {
        trayIcon?.let { icon ->
            SystemTray.getSystemTray().remove(icon)
            trayIcon = null
        }
    }
}
