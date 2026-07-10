package dev.nucleusframework.composenativetray.tray.impl

import dev.nucleusframework.composenativetray.lib.mac.MacTrayManager
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.menu.impl.MacTrayMenuBuilderImpl

object MacTrayInitializer {
    private const val DEFAULT_ID: String = "_default"

    // Manage multiple tray managers and builders by ID
    private val trayManagers: MutableMap<String, MacTrayManager> = mutableMapOf()
    private val trayMenuBuilders: MutableMap<String, MacTrayMenuBuilderImpl> = mutableMapOf()

    // Accessors for per-instance integration
    @Synchronized
    internal fun getManager(id: String): MacTrayManager? = trayManagers[id]

    @Synchronized
    internal fun getNativeTrayHandle(id: String): Long = trayManagers[id]?.getNativeTrayHandle() ?: 0L

    @Synchronized
    fun initialize(
        id: String,
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null,
        onMenuOpened: (() -> Unit)? = null,
    ) {
        var manager = trayManagers[id]
        if (manager == null) {
            // Create a new manager for this ID
            manager = MacTrayManager(iconPath, tooltip, onLeftClick, onMenuOpened)
            trayManagers[id] = manager

            // Build menu for this manager
            val builder =
                MacTrayMenuBuilderImpl(
                    iconPath,
                    tooltip,
                    onLeftClick,
                    trayManager = manager,
                ).apply {
                    menuContent?.invoke(this)
                }
            // Replace old builder for this ID if any
            trayMenuBuilders.remove(id)?.dispose()
            trayMenuBuilders[id] = builder

            val menuItems = builder.build()
            // Add each built item to manager before starting
            menuItems.forEach { manager.addMenuItem(it) }

            // Start the macOS tray for this manager
            manager.startTray()
        } else {
            // Existing manager: delegate to update with the provided content
            update(id, iconPath, tooltip, onLeftClick, menuContent, onMenuOpened)
        }
    }

    @Synchronized
    fun update(
        id: String,
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null,
        onMenuOpened: (() -> Unit)? = null,
    ) {
        val manager = trayManagers[id]
        if (manager == null) {
            // If manager doesn't exist, initialize it
            initialize(id, iconPath, tooltip, onLeftClick, menuContent, onMenuOpened)
            return
        }

        // Rebuild menu if content provided
        val newMenuItems =
            if (menuContent != null) {
                val builder =
                    MacTrayMenuBuilderImpl(
                        iconPath,
                        tooltip,
                        onLeftClick,
                        trayManager = manager,
                    ).apply {
                        menuContent()
                    }
                trayMenuBuilders.remove(id)?.dispose()
                trayMenuBuilders[id] = builder
                builder.build()
            } else {
                null
            }

        manager.update(iconPath, tooltip, onLeftClick, newMenuItems, onMenuOpened)
    }

    @Synchronized
    fun setAppearanceIcons(
        id: String,
        lightIconPath: String,
        darkIconPath: String,
    ) {
        trayManagers[id]?.setAppearanceIcons(lightIconPath, darkIconPath)
    }

    @Synchronized
    fun dispose(id: String) {
        trayMenuBuilders.remove(id)?.dispose()
        trayManagers.remove(id)?.stopTray()
    }

    // Backward-compatible API for existing callers (single default tray)
    fun initialize(
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null,
        onMenuOpened: (() -> Unit)? = null,
    ) = initialize(DEFAULT_ID, iconPath, tooltip, onLeftClick, menuContent, onMenuOpened)

    fun update(
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null,
        onMenuOpened: (() -> Unit)? = null,
    ) = update(DEFAULT_ID, iconPath, tooltip, onLeftClick, menuContent, onMenuOpened)

    fun dispose() = dispose(DEFAULT_ID)
}
