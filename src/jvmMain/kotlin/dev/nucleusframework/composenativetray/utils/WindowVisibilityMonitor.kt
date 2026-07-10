package dev.nucleusframework.composenativetray.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.AWTEvent
import java.awt.Frame
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ComponentEvent
import java.awt.event.WindowEvent

object WindowVisibilityMonitor {
    // Public marker name used to identify the tray popup dialog window so it can be excluded from checks
    const val TRAY_DIALOG_NAME: String = "ComposeTrayPopup"

    // Expose current visibility status as a StateFlow<Boolean>
    private val _hasVisible = MutableStateFlow(false)
    val hasAnyVisibleWindows: StateFlow<Boolean> = _hasVisible

    private val listener =
        AWTEventListener { event ->
            // React to window + component visibility changes
            val type = event.id
            when (type) {
                WindowEvent.WINDOW_OPENED,
                WindowEvent.WINDOW_CLOSED,
                WindowEvent.WINDOW_ICONIFIED,
                WindowEvent.WINDOW_DEICONIFIED,
                WindowEvent.WINDOW_STATE_CHANGED,
                ComponentEvent.COMPONENT_SHOWN,
                ComponentEvent.COMPONENT_HIDDEN,
                -> recompute()
            }
        }

    init {
        // Start listening as soon as the object is loaded
        val mask = AWTEvent.WINDOW_EVENT_MASK or AWTEvent.COMPONENT_EVENT_MASK
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask)
        recompute()
    }

    private fun Window.isEffectivelyVisible(): Boolean {
        val isMinimized = (this is Frame) && ((extendedState and Frame.ICONIFIED) != 0)
        val hasSize = width > 0 && height > 0
        // Consider minimized windows as "effectively visible" for Dock visibility purposes
        return (isShowing && isVisible && isDisplayable && hasSize && opacity > 0f) || isMinimized
    }

    /** Recalculate and publish the current visibility state */
    fun recompute() {
        _hasVisible.value =
            Window.getWindows()
                .asSequence()
                .filter { it.name != TRAY_DIALOG_NAME }
                .any { it.isEffectivelyVisible() }
    }
}
