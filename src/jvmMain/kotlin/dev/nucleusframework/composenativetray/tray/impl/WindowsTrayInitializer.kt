package dev.nucleusframework.composenativetray.tray.impl

import dev.nucleusframework.composenativetray.lib.windows.WindowsTrayManager
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.menu.impl.WindowsTrayMenuBuilderImpl

object WindowsTrayInitializer {
    private const val DEFAULT_ID: String = "_default"

    // Manage multiple tray managers by ID to allow multiple tray icons
    private val trayManagers: MutableMap<String, WindowsTrayManager> = mutableMapOf()

    @Synchronized
    fun initialize(
        id: String,
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null,
        onMenuOpened: (() -> Unit)? = null,
    ) {
        val menuItems =
            WindowsTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick).apply {
                menuContent?.let { it() }
            }.build()

        val manager = trayManagers[id]
        if (manager == null) {
            val windowsTrayManager = WindowsTrayManager(id, iconPath, tooltip, onLeftClick, onMenuOpened)
            trayManagers[id] = windowsTrayManager
            windowsTrayManager.initialize(menuItems)
        } else {
            manager.update(iconPath, tooltip, onLeftClick, onMenuOpened, menuItems)
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
        // Same as initialize - it will handle both cases per ID
        initialize(id, iconPath, tooltip, onLeftClick, menuContent, onMenuOpened)
    }

    @Synchronized
    fun dispose(id: String) {
        trayManagers.remove(id)?.stopTray()
    }

    /**
     * Force a fresh capture of the tray icon position.
     * This is useful when Windows reorganizes icons after creation.
     */
    @Synchronized
    fun refreshPosition(id: String) {
        trayManagers[id]?.refreshPosition()
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
