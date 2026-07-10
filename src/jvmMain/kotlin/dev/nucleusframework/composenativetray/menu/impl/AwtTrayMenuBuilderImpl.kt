package dev.nucleusframework.composenativetray.menu.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import dev.nucleusframework.composenativetray.menu.api.KeyShortcut
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.utils.IconRenderProperties
import org.jetbrains.compose.resources.DrawableResource
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

internal class AwtTrayMenuBuilderImpl(
    private val popupMenu: PopupMenu,
    private val trayIcon: TrayIcon,
) : TrayMenuBuilder {
    override fun Item(
        label: String,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
        onClick: () -> Unit,
    ) {
        val menuItem = MenuItem(label)
        menuItem.isEnabled = isEnabled
        menuItem.addActionListener {
            onClick()
        }
        popupMenu.add(menuItem)
    }

    override fun Item(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
        onClick: () -> Unit,
    ) {
        Item(label, isEnabled, shortcut, onClick)
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
        Item(label, isEnabled, shortcut, onClick)
    }

    override fun Item(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
        onClick: () -> Unit,
    ) {
        Item(label, isEnabled, shortcut, onClick)
    }

    override fun Item(
        label: String,
        icon: DrawableResource,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
        onClick: () -> Unit,
    ) {
        Item(label, isEnabled, shortcut, onClick)
    }

    override fun CheckableItem(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean,
        shortcut: KeyShortcut?,
    ) {
        var currentChecked = checked
        val checkableMenuItem = MenuItem(getCheckableLabel(label, currentChecked))
        checkableMenuItem.isEnabled = isEnabled

        checkableMenuItem.addActionListener {
            val newChecked = !currentChecked
            currentChecked = newChecked
            checkableMenuItem.label = getCheckableLabel(label, newChecked)
            onCheckedChange(newChecked)
        }

        popupMenu.add(checkableMenuItem)
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
        CheckableItem(label, checked, onCheckedChange, isEnabled, shortcut)
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
        CheckableItem(label, checked, onCheckedChange, isEnabled, shortcut)
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
        CheckableItem(label, checked, onCheckedChange, isEnabled, shortcut)
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
        CheckableItem(label, checked, onCheckedChange, isEnabled, shortcut)
    }

    override fun SubMenu(
        label: String,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        val subMenu = PopupMenu(label)
        subMenu.isEnabled = isEnabled
        submenuContent?.let { AwtTrayMenuBuilderImpl(subMenu, trayIcon).apply(it) }
        popupMenu.add(subMenu)
    }

    override fun SubMenu(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        SubMenu(label, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        SubMenu(label, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        SubMenu(label, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        icon: DrawableResource,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?,
    ) {
        SubMenu(label, isEnabled, submenuContent)
    }

    override fun Divider() {
        popupMenu.addSeparator()
    }

    override fun dispose() {
        SystemTray.getSystemTray().remove(trayIcon)
    }

    private fun getCheckableLabel(
        label: String,
        isChecked: Boolean,
    ): String {
        return if (isChecked) "✔ $label" else label
    }
}
