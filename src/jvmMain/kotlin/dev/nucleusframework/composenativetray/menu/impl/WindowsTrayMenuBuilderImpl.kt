package dev.nucleusframework.composenativetray.menu.impl

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import dev.nucleusframework.composenativetray.lib.windows.WindowsTrayManager
import dev.nucleusframework.composenativetray.menu.api.KeyShortcut
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.utils.ComposableIconUtils
import dev.nucleusframework.composenativetray.utils.IconRenderProperties
import dev.nucleusframework.composenativetray.utils.isMenuBarInDarkMode
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class WindowsTrayMenuBuilderImpl(
    private val iconPath: String,
    private val tooltip: String = "",
    private val onLeftClick: (() -> Unit)?,
) : TrayMenuBuilder {
    private val menuItems = mutableListOf<WindowsTrayManager.MenuItem>()
    private val lock = ReentrantLock()

    // Maintain persistent references to prevent GC
    private val persistentMenuItems = mutableListOf<WindowsTrayManager.MenuItem>()

    override fun Item(
        label: String,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
        onClick: () -> Unit,
    ) {
        lock.withLock {
            val menuItem =
                WindowsTrayManager.MenuItem(
                    text = label.withShortcut(shortcut),
                    iconPath = null,
                    isEnabled = isEnabled,
                    onClick = onClick,
                )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    override fun Item(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
        onClick: () -> Unit,
    ) {
        lock.withLock {
            val iconPath = ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)

            val menuItem =
                WindowsTrayManager.MenuItem(
                    text = label.withShortcut(shortcut),
                    iconPath = iconPath,
                    isEnabled = isEnabled,
                    onClick = onClick,
                )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    override fun Item(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
        onClick: () -> Unit,
    ) {
        val iconContent: @Composable () -> Unit = {
            val isDark = isMenuBarInDarkMode()

            Image(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter =
                    iconTint?.let { ColorFilter.tint(it) }
                        ?: if (isDark) {
                            ColorFilter.tint(Color.White)
                        } else {
                            ColorFilter.tint(Color.Black)
                        },
            )
        }

        Item(label, iconContent, iconRenderProperties, isEnabled, shortcut, onClick)
    }

    override fun Item(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
        onClick: () -> Unit,
    ) {
        val iconContent: @Composable () -> Unit = {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Item(label, iconContent, iconRenderProperties, isEnabled, shortcut, onClick)
    }

    override fun Item(
        label: String,
        icon: DrawableResource,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
        onClick: () -> Unit,
    ) {
        val iconContent: @Composable () -> Unit = {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Item(label, iconContent, iconRenderProperties, isEnabled, shortcut, onClick)
    }

    override fun CheckableItem(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
    ) {
        lock.withLock {
            val menuItem =
                WindowsTrayManager.MenuItem(
                    text = label.withShortcut(shortcut),
                    iconPath = null,
                    isEnabled = isEnabled,
                    isCheckable = true,
                    isChecked = checked,
                    onClick = {
                        val newChecked = !checked
                        onCheckedChange(newChecked)
                    },
                )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    override fun CheckableItem(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
    ) {
        lock.withLock {
            val iconPath = ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)

            val menuItem =
                WindowsTrayManager.MenuItem(
                    text = label.withShortcut(shortcut),
                    iconPath = iconPath,
                    isEnabled = isEnabled,
                    isCheckable = true,
                    isChecked = checked,
                    onClick = {
                        val newChecked = !checked
                        onCheckedChange(newChecked)
                    },
                )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    override fun CheckableItem(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
    ) {
        val iconContent: @Composable () -> Unit = {
            val isDark = isMenuBarInDarkMode()

            Image(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter =
                    iconTint?.let { ColorFilter.tint(it) }
                        ?: if (isDark) {
                            ColorFilter.tint(Color.White)
                        } else {
                            ColorFilter.tint(Color.Black)
                        },
            )
        }

        CheckableItem(label, iconContent, iconRenderProperties, checked, onCheckedChange, isEnabled, shortcut)
    }

    override fun CheckableItem(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
    ) {
        val iconContent: @Composable () -> Unit = {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        CheckableItem(label, iconContent, iconRenderProperties, checked, onCheckedChange, isEnabled, shortcut)
    }

    override fun CheckableItem(
        label: String,
        icon: DrawableResource,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
    ) {
        val iconContent: @Composable () -> Unit = {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        CheckableItem(label, iconContent, iconRenderProperties, checked, onCheckedChange, isEnabled, shortcut)
    }

    override fun SubMenu(
        label: String,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        createSubMenu(label, null, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        val iconPath = ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)
        createSubMenu(label, iconPath, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        val iconContent: @Composable () -> Unit = {
            val isDark = isMenuBarInDarkMode()

            Image(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter =
                    iconTint?.let { ColorFilter.tint(it) }
                        ?: if (isDark) {
                            ColorFilter.tint(Color.White)
                        } else {
                            ColorFilter.tint(Color.Black)
                        },
            )
        }

        SubMenu(label, iconContent, iconRenderProperties, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        val iconContent: @Composable () -> Unit = {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        SubMenu(label, iconContent, iconRenderProperties, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        icon: DrawableResource,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        val iconContent: @Composable () -> Unit = {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SubMenu(label, iconContent, iconRenderProperties, isEnabled, submenuContent)
    }

    private fun createSubMenu(
        label: String,
        iconPath: String?,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        val subMenuItems = mutableListOf<WindowsTrayManager.MenuItem>()
        if (submenuContent != null) {
            val subMenuImpl =
                WindowsTrayMenuBuilderImpl(
                    this.iconPath,
                    tooltip,
                    onLeftClick = onLeftClick,
                ).apply(submenuContent)
            subMenuItems.addAll(subMenuImpl.menuItems)
        }
        lock.withLock {
            val subMenu =
                WindowsTrayManager.MenuItem(
                    text = label,
                    iconPath = iconPath,
                    isEnabled = isEnabled,
                    subMenuItems = subMenuItems,
                )
            menuItems.add(subMenu)
            persistentMenuItems.add(subMenu)
        }
    }

    override fun Divider() {
        lock.withLock {
            val divider = WindowsTrayManager.MenuItem(text = "-")
            menuItems.add(divider)
            persistentMenuItems.add(divider)
        }
    }

    override fun dispose() {
        lock.withLock {
            WindowsTrayManager(
                instanceId = "_temp",
                iconPath = iconPath,
                tooltip = tooltip,
                onLeftClick = onLeftClick,
            ).stopTray()
            persistentMenuItems.clear()
        }
    }

    fun build(): List<WindowsTrayManager.MenuItem> = lock.withLock { menuItems.toList() }

    companion object {
        /**
         * Appends Win32 tab-separated accelerator text to a menu label.
         * Windows natively right-aligns everything after `\t` in menu item strings.
         */
        private fun String.withShortcut(shortcut: KeyShortcut?): String =
            if (shortcut != null) "$this\t${shortcut.toWindowsAcceleratorText()}" else this
    }
}
