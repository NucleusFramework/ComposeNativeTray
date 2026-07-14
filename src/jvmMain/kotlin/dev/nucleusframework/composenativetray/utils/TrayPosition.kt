package dev.nucleusframework.composenativetray.utils

import dev.nucleusframework.composenativetray.lib.mac.MacNativeBridge
import dev.nucleusframework.composenativetray.lib.windows.WindowsNativeBridge
import dev.nucleusframework.core.runtime.LinuxDesktopEnvironment
import dev.nucleusframework.core.runtime.Platform
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

enum class TrayPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** A tray-icon click position in the native backend's coordinates (physical px on Windows, logical elsewhere). */
data class TrayClickPoint(
    val x: Int,
    val y: Int,
)

/**
 * Records the last tray-icon click reported by the native backends.
 *
 * The core tray itself doesn't need screen geometry; only `TrayApp`'s popup does. So the native
 * managers record the raw click coordinates here and the `composenativetray-app` module reads them
 * to compute the popup position against the (Tao-backed) screen geometry it owns.
 */
object TrayClickTracker {
    private val last = AtomicReference<TrayClickPoint?>(null)
    private val perInstance: MutableMap<String, TrayClickPoint> =
        Collections.synchronizedMap(mutableMapOf())

    fun recordClick(
        x: Int,
        y: Int,
    ) {
        last.set(TrayClickPoint(x, y))
    }

    fun recordClick(
        instanceId: String,
        x: Int,
        y: Int,
    ) {
        val point = TrayClickPoint(x, y)
        perInstance[instanceId] = point
        last.set(point)
    }

    fun lastClick(): TrayClickPoint? = last.get()

    fun lastClick(instanceId: String): TrayClickPoint? = perInstance[instanceId]
}

internal fun getWindowsTrayPosition(nativeResult: String?): TrayPosition =
    when (nativeResult) {
        null -> throw IllegalArgumentException("Returned value is null")
        "top-left" -> TrayPosition.TOP_LEFT
        "top-right" -> TrayPosition.TOP_RIGHT
        "bottom-left" -> TrayPosition.BOTTOM_LEFT
        "bottom-right" -> TrayPosition.BOTTOM_RIGHT
        else -> throw IllegalArgumentException("Unknown value: $nativeResult")
    }

internal fun getMacTrayPosition(nativeResult: String?): TrayPosition =
    when (nativeResult) {
        "top-left" -> TrayPosition.TOP_LEFT
        "top-right" -> TrayPosition.TOP_RIGHT
        else -> TrayPosition.TOP_RIGHT
    }

/**
 * OS → tray corner heuristic.
 *
 * Windows/macOS resolve the corner from the native tray region (no screen geometry needed). Linux
 * has no reliable native tray-region API, so it uses the desktop-environment convention. Precise,
 * click-derived Linux positioning is provided by `TrayApp` (composenativetray-app), which owns the
 * Tao screen geometry.
 */
fun getTrayPosition(): TrayPosition =
    when (Platform.Current) {
        Platform.Windows -> getWindowsTrayPosition(WindowsNativeBridge.nativeGetNotificationIconsRegion())
        Platform.MacOS -> getMacTrayPosition(MacNativeBridge.nativeGetStatusItemRegion())
        Platform.Linux ->
            when (LinuxDesktopEnvironment.Current) {
                LinuxDesktopEnvironment.KDE -> TrayPosition.BOTTOM_RIGHT
                LinuxDesktopEnvironment.Cinnamon -> TrayPosition.BOTTOM_RIGHT
                LinuxDesktopEnvironment.Gnome -> TrayPosition.TOP_RIGHT
                LinuxDesktopEnvironment.Mate -> TrayPosition.TOP_RIGHT
                LinuxDesktopEnvironment.XFCE -> TrayPosition.TOP_RIGHT
                else -> TrayPosition.TOP_RIGHT
            }
        Platform.Unknown -> TrayPosition.TOP_RIGHT
        else -> TrayPosition.TOP_RIGHT
    }
