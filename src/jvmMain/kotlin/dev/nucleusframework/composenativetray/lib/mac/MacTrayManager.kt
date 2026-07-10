package dev.nucleusframework.composenativetray.lib.mac

import androidx.compose.runtime.mutableStateOf
import dev.nucleusframework.composenativetray.menu.api.KeyShortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class MacTrayManager(
    private var iconPath: String,
    private var tooltip: String = "",
    onLeftClick: (() -> Unit)? = null,
    onMenuOpened: (() -> Unit)? = null,
) {
    private var trayHandle: Long = 0L
    private var menuHandle: Long = 0L
    private var menuItemCount: Int = 0
    private val menuItems: MutableList<MenuItem> = mutableListOf()
    private val running = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private var trayThread: Thread? = null
    private val initLatch = CountDownLatch(1)

    // Coroutine scopes for callback handling
    private var mainScope: CoroutineScope? = null
    private var ioScope: CoroutineScope? = null

    // Keep submenu handles for cleanup
    private val submenuHandles: MutableList<Pair<Long, Int>> = mutableListOf()

    private val onLeftClickCallback = mutableStateOf(onLeftClick)
    private var onMenuOpenedCallback: (() -> Unit)? = onMenuOpened

    // Top level MenuItem class
    data class MenuItem(
        val text: String,
        val icon: String? = null,
        val isEnabled: Boolean = true,
        val isCheckable: Boolean = false,
        val isChecked: Boolean = false,
        val shortcut: KeyShortcut? = null,
        val onClick: (() -> Unit)? = null,
        val subMenuItems: List<MenuItem> = emptyList(),
    )

    fun addMenuItem(menuItem: MenuItem) {
        lock.withLock {
            menuItems.add(menuItem)
        }
    }

    // Update a menu item's checked state
    fun updateMenuItemCheckedState(
        label: String,
        isChecked: Boolean,
    ) {
        lock.withLock {
            val index = menuItems.indexOfFirst { it.text == label }
            if (index != -1) {
                menuItems[index] = menuItems[index].copy(isChecked = isChecked)
                // Recreate the menu to reflect changes
                recreateMenu()
            }
        }
    }

    // Update the tray with new properties and menu items
    fun update(
        newIconPath: String,
        newTooltip: String,
        newOnLeftClick: (() -> Unit)?,
        newMenuItems: List<MenuItem>? = null,
        newOnMenuOpened: (() -> Unit)? = null,
    ) {
        lock.withLock {
            if (!running.get() || trayHandle == 0L) return

            // Update properties
            val iconChanged = this.iconPath != newIconPath
            val tooltipChanged = this.tooltip != newTooltip
            val onLeftClickChanged = this.onLeftClickCallback.value != newOnLeftClick
            val onMenuOpenedChanged = this.onMenuOpenedCallback != newOnMenuOpened

            // Update icon path and tooltip
            this.iconPath = newIconPath
            this.tooltip = newTooltip
            this.onLeftClickCallback.value = newOnLeftClick
            this.onMenuOpenedCallback = newOnMenuOpened

            if (iconChanged) {
                MacNativeBridge.nativeSetTrayIcon(trayHandle, newIconPath)
            }
            if (tooltipChanged) {
                MacNativeBridge.nativeSetTrayTooltip(trayHandle, newTooltip)
            }
            if (onLeftClickChanged) {
                initializeOnLeftClickCallback()
            }
            if (onMenuOpenedChanged) {
                initializeOnMenuOpenedCallback()
            }

            // Update menu items if provided
            if (newMenuItems != null) {
                menuItems.clear()
                menuItems.addAll(newMenuItems)
                recreateMenu()
            } else if (iconChanged || tooltipChanged || onLeftClickChanged) {
                MacNativeBridge.nativeUpdateTray(trayHandle)
            }
        }
    }

    // Recreate the menu with updated state
    private fun recreateMenu() {
        if (!running.get() || trayHandle == 0L) return

        // Free old menu
        freeCurrentMenu()

        // Recreate the menu
        initializeTrayMenu()

        // Update the tray
        MacNativeBridge.nativeUpdateTray(trayHandle)
    }

    private fun freeCurrentMenu() {
        // Free submenus first (in reverse order to handle nested)
        for ((handle, count) in submenuHandles.reversed()) {
            MacNativeBridge.nativeFreeMenuItems(handle, count)
        }
        submenuHandles.clear()

        // Free top-level menu
        if (menuHandle != 0L) {
            MacNativeBridge.nativeFreeMenuItems(menuHandle, menuItemCount)
            menuHandle = 0L
            menuItemCount = 0
        }
    }

    // Start the tray
    fun startTray() {
        lock.withLock {
            if (running.get()) {
                return
            }

            running.set(true)

            // Create new coroutine scopes
            mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            // Create and start the tray thread
            trayThread =
                Thread {
                    try {
                        // Create tray structure via JNI
                        trayHandle = MacNativeBridge.nativeCreateTray(iconPath, tooltip)
                        if (trayHandle == 0L) {
                            throw IllegalStateException("Failed to allocate native tray struct")
                        }

                        initializeOnLeftClickCallback()
                        initializeTrayMenu()

                        val initResult = MacNativeBridge.nativeInitTray(trayHandle)
                        if (initResult != 0) {
                            throw IllegalStateException("Failed to initialize tray: $initResult")
                        }

                        // Set menu-opened callback after init (TrayContext must exist)
                        initializeOnMenuOpenedCallback()

                        // Signal that initialization is complete
                        initLatch.countDown()

                        // Run the event loop
                        while (running.get()) {
                            val result = MacNativeBridge.nativeLoopTray(0)
                            if (result != 0) {
                                break
                            }
                            Thread.sleep(100)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        cleanupTray()
                    }
                }.apply {
                    name = "MacTray-Thread"
                    isDaemon = true
                    start()
                }

            // Wait for initialization to complete
            try {
                initLatch.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun initializeOnLeftClickCallback() {
        if (trayHandle == 0L) return

        val onClick = onLeftClickCallback.value
        if (onClick != null) {
            MacNativeBridge.nativeSetTrayCallback(
                trayHandle,
                Runnable {
                    mainScope?.launch {
                        ioScope?.launch {
                            onClick()
                        }
                    }
                },
            )
        } else {
            MacNativeBridge.nativeSetTrayCallback(trayHandle, null)
        }
    }

    private fun initializeOnMenuOpenedCallback() {
        if (trayHandle == 0L) return

        val callback = onMenuOpenedCallback
        if (callback != null) {
            MacNativeBridge.nativeSetMenuOpenedCallback(
                trayHandle,
                Runnable {
                    mainScope?.launch {
                        ioScope?.launch {
                            callback()
                        }
                    }
                },
            )
        } else {
            MacNativeBridge.nativeSetMenuOpenedCallback(trayHandle, null)
        }
    }

    private fun initializeTrayMenu() {
        if (trayHandle == 0L) return

        if (menuItems.isEmpty()) {
            MacNativeBridge.nativeClearTrayMenu(trayHandle)
            return
        }

        val count = menuItems.size
        val handle = MacNativeBridge.nativeCreateMenuItems(count)
        menuHandle = handle
        menuItemCount = count

        menuItems.forEachIndexed { index, item ->
            initializeNativeMenuItem(handle, index, item)
        }

        MacNativeBridge.nativeSetTrayMenu(trayHandle, handle)
    }

    private fun initializeNativeMenuItem(
        parentHandle: Long,
        index: Int,
        menuItem: MenuItem,
    ) {
        MacNativeBridge.nativeSetMenuItem(
            parentHandle,
            index,
            menuItem.text,
            menuItem.icon,
            if (menuItem.isEnabled) 0 else 1,
            if (menuItem.isChecked) 1 else 0,
        )

        menuItem.shortcut?.let { shortcut ->
            MacNativeBridge.nativeSetMenuItemShortcut(
                parentHandle,
                index,
                shortcut.toMacKeyEquivalent(),
                shortcut.toMacModifierMask(),
            )
        }

        menuItem.onClick?.let { onClick ->
            MacNativeBridge.nativeSetMenuItemCallback(
                parentHandle,
                index,
                Runnable {
                    if (!running.get()) return@Runnable
                    mainScope?.launch {
                        ioScope?.launch {
                            onClick()
                        }
                    }
                },
            )
        }

        if (menuItem.subMenuItems.isNotEmpty()) {
            val subCount = menuItem.subMenuItems.size
            val subHandle = MacNativeBridge.nativeCreateMenuItems(subCount)
            submenuHandles.add(subHandle to subCount)

            menuItem.subMenuItems.forEachIndexed { subIndex, subItem ->
                initializeNativeMenuItem(subHandle, subIndex, subItem)
            }

            MacNativeBridge.nativeSetMenuItemSubmenu(parentHandle, index, subHandle)
        }
    }

    private fun cleanupTray() {
        lock.withLock {
            if (trayHandle != 0L) {
                try {
                    // Free menu first
                    freeCurrentMenu()
                    // Dispose the tray (this frees the struct too)
                    MacNativeBridge.nativeDisposeTray(trayHandle)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                trayHandle = 0L
            }
            menuItems.clear()
        }
    }

    fun stopTray() {
        lock.withLock {
            if (!running.get()) {
                return
            }

            running.set(false)
        }

        // Wait for the tray thread to finish
        trayThread?.let { thread ->
            try {
                thread.join(5000) // Wait up to 5 seconds
                if (thread.isAlive) {
                    thread.interrupt()
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        // Cancel coroutines
        mainScope?.cancel()
        ioScope?.cancel()
        mainScope = null
        ioScope = null
        trayThread = null
    }

    fun getNativeTrayHandle(): Long {
        return trayHandle
    }

    fun setAppearanceIcons(
        lightIconPath: String,
        darkIconPath: String,
    ) {
        lock.withLock {
            if (trayHandle == 0L) return
            MacNativeBridge.nativeSetIconsForAppearance(trayHandle, lightIconPath, darkIconPath)
        }
    }
}
