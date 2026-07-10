package dev.nucleusframework.composenativetray.lib.windows

import dev.nucleusframework.core.runtime.Platform
import java.awt.Window
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WindowsOutsideClickWatcher using a low-level mouse hook (WH_MOUSE_LL) via JNI.
 *
 * Behavior:
 *  - Listens for global left-button *down* events.
 *  - If the click occurs outside the supplied window (and not ignored by predicate), invokes onOutsideClick().
 *
 * Public signatures are preserved.
 */
class WindowsOutsideClickWatcher(
    private val windowSupplier: () -> Window?,
    private val onOutsideClick: () -> Unit,
    private val ignorePointPredicate: ((x: Int, y: Int) -> Boolean)? = null,
) : AutoCloseable {
    @Volatile private var hookThread: Thread? = null

    @Volatile private var hookId: Long = 0L
    private val stopping = AtomicBoolean(false)

    /** Start the global low-level mouse hook on a dedicated daemon thread. */
    fun start() {
        if (Platform.Current != Platform.Windows) return
        synchronized(this) {
            if (hookThread != null) return
            stopping.set(false)

            hookThread =
                Thread({
                    val callback =
                        Runnable {
                            try {
                                val xy = IntArray(2)
                                WindowsNativeBridge.nativeGetLastMouseHookClick(xy)
                                val px = xy[0]
                                val py = xy[1]

                                val win = windowSupplier()
                                if (win != null && win.isShowing) {
                                    val winLoc =
                                        try {
                                            win.locationOnScreen
                                        } catch (_: Throwable) {
                                            null
                                        }
                                    if (winLoc != null) {
                                        // Get the graphics configuration to determine the DPI scale
                                        val scale =
                                            try {
                                                win.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
                                            } catch (_: Throwable) {
                                                1.0
                                            }

                                        // Convert window bounds from logical to physical pixels
                                        val wx = (winLoc.x * scale).toInt()
                                        val wy = (winLoc.y * scale).toInt()
                                        val ww = (win.width * scale).toInt()
                                        val wh = (win.height * scale).toInt()

                                        val insideWindow = px in wx until (wx + ww) && py in wy until (wy + wh)
                                        val ignored = ignorePointPredicate?.invoke(px, py) == true

                                        if (!insideWindow && !ignored) {
                                            // Let caller decide EDT marshaling.
                                            onOutsideClick()
                                        }
                                    }
                                }
                            } catch (_: Throwable) {
                                // Never crash the hook callback
                            }
                        }

                    hookId = WindowsNativeBridge.nativeInstallMouseHook(callback)
                    if (hookId == 0L) return@Thread

                    // Block on message loop until stopped
                    WindowsNativeBridge.nativeRunMouseHookLoop(hookId)
                }, "WindowsOutsideClickWatcher-LL").apply {
                    isDaemon = true
                    start()
                }
        }
    }

    /** Stop the hook (alias to close()). */
    fun stop() = close()

    /** Uninstalls the hook and signals the hook thread to exit. */
    override fun close() {
        synchronized(this) {
            stopping.set(true)

            val id = hookId
            if (id != 0L) {
                try {
                    WindowsNativeBridge.nativeStopMouseHook(id)
                } catch (_: Throwable) {
                }
                hookId = 0L
            }

            hookThread = null
        }
    }
}
