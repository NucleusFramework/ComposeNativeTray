package dev.nucleusframework.composenativetray.utils

import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import java.awt.Window

object WindowRaise {
    fun raise(window: Window) {
        try {
            // Portable trick: temporarily make it topmost.
            window.isAlwaysOnTop = true
            window.toFront()
            window.requestFocus()
            window.requestFocusInWindow()
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun unraise(window: Window) {
        try {
            window.isAlwaysOnTop = false
        } catch (_: Throwable) {
        }
    }

    /**
     * Convenience helper to raise a window, give the window manager a brief moment,
     * then revert the temporary always-on-top flag. Useful especially on Windows.
     */
    suspend fun forceFront(
        window: Window,
        windowState: WindowState,
        delayMs: Long = 250,
    ) {
        // Ensure it’s not minimized
        windowState.isMinimized = false

        // Raise first
        raise(window)
        try {
            // Give the WM a short moment to apply stacking/focus
            delay(delayMs)
        } catch (_: Throwable) {
            // ignore any coroutine cancellation or other issues
        }
        // Then revert
        unraise(window)
    }
}
