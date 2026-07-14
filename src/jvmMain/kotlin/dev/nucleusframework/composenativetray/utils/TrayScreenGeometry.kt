package dev.nucleusframework.composenativetray.utils

internal data class ScreenRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * Screen geometry used to place `TrayApp`'s popup window and to detect which screen corner the
 * tray icon was clicked in.
 *
 * The native query is backed by the Tao windowing backend, which lives in the separate
 * `composenativetray-app` module — that module injects [scaleProvider] / [workAreaPxProvider].
 * When no provider is set (core used on its own, without `TrayApp`) the values fall back to sane
 * defaults so corner detection degrades gracefully instead of pulling a windowing dependency into
 * the core artifact.
 *
 * Public only so the tray-app module can install the providers; not part of the stable API.
 */
object TrayScreenGeometry {
    /** Primary monitor scale factor provider (installed by the tray-app module). */
    @Volatile
    var scaleProvider: (() -> Float)? = null

    /**
     * Primary monitor work-area provider in **physical** pixels as `[x, y, width, height]`
     * (installed by the tray-app module). Returns `null` when unavailable.
     */
    @Volatile
    var workAreaPxProvider: (() -> LongArray?)? = null

    /** Primary monitor scale factor (1.0 on non-HiDPI displays or when no provider is set). */
    internal fun scale(): Float =
        runCatching { scaleProvider?.invoke() ?: 1f }
            .getOrDefault(1f)
            .coerceAtLeast(1f)

    /**
     * Primary monitor work area (screen minus taskbar/menu bar/panel) in logical pixels, top-left
     * origin. Falls back to a common resolution when no provider is set or the query fails, so
     * positioning degrades gracefully.
     */
    internal fun workAreaLogical(): ScreenRect {
        val wa = runCatching { workAreaPxProvider?.invoke() }.getOrNull()
        if (wa == null || wa.size != 4) return ScreenRect(0, 0, FALLBACK_WIDTH, FALLBACK_HEIGHT)
        val s = scale()
        return ScreenRect(
            (wa[0] / s).toInt(),
            (wa[1] / s).toInt(),
            (wa[2] / s).toInt(),
            (wa[3] / s).toInt(),
        )
    }

    private const val FALLBACK_WIDTH = 1920
    private const val FALLBACK_HEIGHT = 1080
}
