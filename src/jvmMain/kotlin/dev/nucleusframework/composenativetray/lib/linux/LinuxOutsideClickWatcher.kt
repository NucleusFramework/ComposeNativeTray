package dev.nucleusframework.composenativetray.lib.linux

import dev.nucleusframework.composenativetray.utils.isPointWithinLinuxStatusItem
import dev.nucleusframework.core.runtime.Platform
import java.awt.Window
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * LinuxOutsideClickWatcher: X11/XWayland implementation that detects a left-click anywhere,
 * and if it is outside the supplied window (and not on the tray icon area), invokes a callback.
 *
 * Uses JNI via LinuxNativeBridge for X11 calls (no JNA dependency).
 *
 * Notes:
 * - Requires X11/XWayland (DISPLAY must be set). Will no-op on Wayland-only sessions without XWayland.
 * - Polls with XQueryPointer at ~60 Hz, reading Button1Mask for "left pressed".
 */
class LinuxOutsideClickWatcher(
    private val windowSupplier: () -> Window?,
    private val onOutsideClick: () -> Unit,
) : AutoCloseable {
    private var scheduler: ScheduledExecutorService? = null
    private var prevLeft = false

    // X11 state (native handles)
    private var displayHandle: Long = 0L
    private var rootWindow: Long = 0L

    fun start() {
        if (Platform.Current != Platform.Linux) return
        if (scheduler != null) return

        try {
            displayHandle = LinuxNativeBridge.nativeX11OpenDisplay()
            if (displayHandle == 0L) return
            rootWindow = LinuxNativeBridge.nativeX11DefaultRootWindow(displayHandle)
        } catch (_: Throwable) {
            displayHandle = 0L
            return
        }

        scheduler =
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "LinuxOutsideClickWatcher").apply { isDaemon = true }
            }.also { exec ->
                exec.scheduleAtFixedRate({ pollOnce() }, 0, 16, TimeUnit.MILLISECONDS)
            }
    }

    private fun pollOnce() {
        if (displayHandle == 0L) return
        try {
            val outData = IntArray(3) // [rootX, rootY, mask]
            val ok = LinuxNativeBridge.nativeX11QueryPointer(displayHandle, rootWindow, outData) != 0
            if (!ok) return

            val px = outData[0]
            val py = outData[1]
            val mask = outData[2]
            val left = (mask and BUTTON1_MASK) != 0

            // Rising edge: only act once when the button goes down
            if (left && left != prevLeft) {
                val win = windowSupplier.invoke()
                if (win != null && win.isShowing) {
                    val winLoc =
                        try {
                            win.locationOnScreen
                        } catch (_: Throwable) {
                            null
                        }
                    if (winLoc != null) {
                        val wx = winLoc.x
                        val wy = winLoc.y
                        val ww = win.width
                        val wh = win.height

                        val insideWindow = px >= wx && px < wx + ww && py >= wy && py < wy + wh
                        val onTrayIcon =
                            try {
                                isPointWithinLinuxStatusItem(px, py)
                            } catch (_: Throwable) {
                                false
                            }

                        if (!insideWindow && !onTrayIcon) {
                            onOutsideClick.invoke()
                        }
                    }
                }
            }

            prevLeft = left
        } catch (_: Throwable) {
            // Swallow errors to keep the scheduler alive
        }
    }

    fun stop() = close()

    override fun close() {
        try {
            scheduler?.shutdownNow()
        } catch (_: Throwable) {
        }
        scheduler = null

        try {
            if (displayHandle != 0L) LinuxNativeBridge.nativeX11CloseDisplay(displayHandle)
        } catch (_: Throwable) {
        } finally {
            displayHandle = 0L
            rootWindow = 0L
        }
    }

    private companion object {
        // X11 button mask bit for Button1 (left)
        private const val BUTTON1_MASK = 1 shl 8
    }
}
