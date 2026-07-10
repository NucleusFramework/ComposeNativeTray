package dev.nucleusframework.composenativetray.utils

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import dev.nucleusframework.composenativetray.lib.mac.MacNativeBridge
import dev.nucleusframework.composenativetray.lib.windows.WindowsNativeBridge
import dev.nucleusframework.composenativetray.tray.impl.MacTrayInitializer
import io.github.kdroidfilter.platformtools.LinuxDesktopEnvironment
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.detectLinuxDesktopEnvironment
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.io.File
import java.util.Collections
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

enum class TrayPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

data class TrayClickPosition(
    val x: Int,
    val y: Int,
    val position: TrayPosition,
)

internal object TrayClickTracker {
    private val lastClickPosition = AtomicReference<TrayClickPosition?>(null)
    private val perInstancePositions: MutableMap<String, TrayClickPosition> =
        Collections.synchronizedMap(mutableMapOf())

    fun updateClickPosition(
        x: Int,
        y: Int,
    ) {
        val bounds = getScreenBoundsAt(x, y)
        val position = convertPositionToCorner(x - bounds.x, y - bounds.y, bounds.width, bounds.height)
        val pos = TrayClickPosition(x, y, position)
        lastClickPosition.set(pos)
        runCatching { saveTrayClickPosition(x, y, position) }
    }

    fun updateClickPosition(
        instanceId: String,
        x: Int,
        y: Int,
    ) {
        val bounds = getScreenBoundsAt(x, y)
        val position = convertPositionToCorner(x - bounds.x, y - bounds.y, bounds.width, bounds.height)
        val pos = TrayClickPosition(x, y, position)
        perInstancePositions[instanceId] = pos
        lastClickPosition.set(pos)
        runCatching { saveTrayClickPosition(x, y, position) }
    }

    fun setClickPosition(
        x: Int,
        y: Int,
        position: TrayPosition,
    ) {
        val pos = TrayClickPosition(x, y, position)
        lastClickPosition.set(pos)
        runCatching { saveTrayClickPosition(x, y, position) }
    }

    fun setClickPosition(
        instanceId: String,
        x: Int,
        y: Int,
        position: TrayPosition,
    ) {
        val pos = TrayClickPosition(x, y, position)
        perInstancePositions[instanceId] = pos
        lastClickPosition.set(pos)
        runCatching { saveTrayClickPosition(x, y, position) }
    }

    fun getLastClickPosition(): TrayClickPosition? = lastClickPosition.get()

    fun getLastClickPosition(instanceId: String): TrayClickPosition? = perInstancePositions[instanceId]
}

/**
 * Get logical screen size (DPI-independent on Windows).
 * On Windows, Toolkit.getDefaultToolkit().screenSize returns logical coordinates.
 */
private fun getLogicalScreenSize(): java.awt.Dimension {
    return Toolkit.getDefaultToolkit().screenSize
}

/**
 * Returns the bounds of the screen that contains the given point.
 * Falls back to the primary screen if the point is not on any screen.
 */
private fun getScreenBoundsAt(
    x: Int,
    y: Int,
): Rectangle {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
    for (gd in ge.screenDevices) {
        val bounds = gd.defaultConfiguration.bounds
        if (bounds.contains(x, y)) return bounds
    }
    val primary = Toolkit.getDefaultToolkit().screenSize
    return Rectangle(0, 0, primary.width, primary.height)
}

internal fun convertPositionToCorner(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): TrayPosition {
    // Use smarter margins based on typical taskbar/panel size
    // 100px from edge = probably within taskbar/panel area
    val edgeThreshold = 100

    val isNearTop = y < edgeThreshold
    val isNearBottom = y > height - edgeThreshold
    val isNearLeft = x < edgeThreshold
    val isNearRight = x > width - edgeThreshold

    return when {
        // Strong edge detection first
        isNearTop && isNearLeft -> TrayPosition.TOP_LEFT
        isNearTop && isNearRight -> TrayPosition.TOP_RIGHT
        isNearTop -> TrayPosition.TOP_RIGHT // Default top to right
        isNearBottom && isNearLeft -> TrayPosition.BOTTOM_LEFT
        isNearBottom && isNearRight -> TrayPosition.BOTTOM_RIGHT
        isNearBottom -> TrayPosition.BOTTOM_RIGHT // Default bottom to right
        // Fallback: use quadrant-based detection
        x >= width / 2 && y < height / 2 -> TrayPosition.TOP_RIGHT
        x < width / 2 && y < height / 2 -> TrayPosition.TOP_LEFT
        x >= width / 2 -> TrayPosition.BOTTOM_RIGHT
        else -> TrayPosition.BOTTOM_LEFT
    }
}

private const val PROPERTIES_FILE = "tray_position.properties"
private const val POSITION_KEY = "TrayPosition"
private const val X_KEY = "TrayX"
private const val Y_KEY = "TrayY"

private fun trayPropertiesFile(): File {
    val appId = AppIdProvider.appId()
    val tmpBase = System.getProperty("java.io.tmpdir") ?: "."
    val tmpDir = File(File(tmpBase, "ComposeNativeTray"), appId)
    val macCacheDir = macCacheDir()?.resolve(appId)
    val candidates = listOfNotNull(tmpDir, macCacheDir)
    for (dir in candidates) {
        runCatching { if (!dir.exists()) dir.mkdirs() }
        if (dir.exists() && dir.canWrite()) return File(dir, PROPERTIES_FILE)
    }
    return File(tmpDir, PROPERTIES_FILE)
}

private fun legacyPropertiesFile(): File = File(PROPERTIES_FILE)

private fun oldTmpPropertiesFile(): File {
    val tmpBase = System.getProperty("java.io.tmpdir") ?: "."
    val oldDir = File(tmpBase, "ComposeNativeTray")
    return File(oldDir, PROPERTIES_FILE)
}

private fun macCachePropertiesFile(): File? {
    val appId = AppIdProvider.appId()
    val dir = macCacheDir()?.resolve(appId) ?: return null
    return File(dir, PROPERTIES_FILE)
}

private fun macCacheDir(): File? {
    val userHome = System.getProperty("user.home") ?: return null
    return File(userHome).resolve("Library").resolve("Caches").resolve("ComposeNativeTray")
}

private fun loadPropertiesFrom(file: File): Properties? {
    if (!file.exists()) return null
    return runCatching {
        Properties().apply { file.inputStream().use(::load) }
    }.getOrNull()
}

private fun storePropertiesTo(
    file: File,
    props: Properties,
) {
    file.parentFile?.let { runCatching { if (!it.exists()) it.mkdirs() } }
    runCatching { file.outputStream().use { props.store(it, null) } }
}

internal fun saveTrayPosition(position: TrayPosition) {
    val preferredFile = trayPropertiesFile()
    val properties =
        loadPropertiesFrom(preferredFile)
            ?: macCachePropertiesFile()?.let { loadPropertiesFrom(it) }
            ?: loadPropertiesFrom(oldTmpPropertiesFile())
            ?: loadPropertiesFrom(legacyPropertiesFile())
            ?: Properties()
    properties.setProperty(POSITION_KEY, position.name)
    storePropertiesTo(preferredFile, properties)
}

internal fun saveTrayClickPosition(
    x: Int,
    y: Int,
    position: TrayPosition,
) {
    val preferredFile = trayPropertiesFile()
    val properties =
        loadPropertiesFrom(preferredFile)
            ?: macCachePropertiesFile()?.let { loadPropertiesFrom(it) }
            ?: loadPropertiesFrom(oldTmpPropertiesFile())
            ?: loadPropertiesFrom(legacyPropertiesFile())
            ?: Properties()
    properties.setProperty(POSITION_KEY, position.name)
    properties.setProperty(X_KEY, x.toString())
    properties.setProperty(Y_KEY, y.toString())
    storePropertiesTo(preferredFile, properties)
}

internal fun loadTrayClickPosition(): TrayClickPosition? {
    val props =
        loadPropertiesFrom(trayPropertiesFile())
            ?: macCachePropertiesFile()?.let { loadPropertiesFrom(it) }
            ?: loadPropertiesFrom(oldTmpPropertiesFile())
            ?: loadPropertiesFrom(legacyPropertiesFile()) ?: return null

    val positionStr = props.getProperty(POSITION_KEY) ?: return null
    val x = props.getProperty(X_KEY)?.toIntOrNull() ?: return null
    val y = props.getProperty(Y_KEY)?.toIntOrNull() ?: return null

    return try {
        TrayClickPosition(x, y, TrayPosition.valueOf(positionStr))
    } catch (_: IllegalArgumentException) {
        null
    }
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

/** OS → Tray corner heuristics */
fun getTrayPosition(): TrayPosition {
    return when (getOperatingSystem()) {
        OperatingSystem.WINDOWS -> getWindowsTrayPosition(WindowsNativeBridge.nativeGetNotificationIconsRegion())
        OperatingSystem.MACOS -> getMacTrayPosition(MacNativeBridge.nativeGetStatusItemRegion())
        OperatingSystem.LINUX -> {
            TrayClickTracker.getLastClickPosition()?.position
                ?: loadTrayClickPosition()?.position
                ?: run {
                    val props =
                        loadPropertiesFrom(trayPropertiesFile())
                            ?: macCachePropertiesFile()?.let { loadPropertiesFrom(it) }
                            ?: loadPropertiesFrom(oldTmpPropertiesFile())
                            ?: loadPropertiesFrom(legacyPropertiesFile())
                    props?.getProperty(POSITION_KEY)?.let {
                        runCatching { TrayPosition.valueOf(it) }.getOrNull()
                    }
                }
                ?: when (detectLinuxDesktopEnvironment()) {
                    LinuxDesktopEnvironment.KDE -> TrayPosition.BOTTOM_RIGHT
                    LinuxDesktopEnvironment.CINNAMON -> TrayPosition.BOTTOM_RIGHT
                    LinuxDesktopEnvironment.GNOME -> TrayPosition.TOP_RIGHT
                    LinuxDesktopEnvironment.MATE -> TrayPosition.TOP_RIGHT
                    LinuxDesktopEnvironment.XFCE -> TrayPosition.TOP_RIGHT
                    else -> TrayPosition.TOP_RIGHT
                }
        }
        OperatingSystem.UNKNOWN -> TrayPosition.TOP_RIGHT
        else -> TrayPosition.TOP_RIGHT
    }
}

/** Position globale (sans instance) + offsets */
fun getTrayWindowPosition(
    windowWidth: Int,
    windowHeight: Int,
    horizontalOffset: Int = 0,
    verticalOffset: Int = 0,
): WindowPosition {
    val screenSize = Toolkit.getDefaultToolkit().screenSize

    if (getOperatingSystem() == OperatingSystem.WINDOWS) {
        val freshPos =
            TrayClickTracker.getLastClickPosition()
                ?: loadTrayClickPosition()

        val posToUse =
            freshPos ?: run {
                // No click yet (e.g., initiallyVisible = true). Synthesize one near the tray corner
                val corner = getTrayPosition()
                val (sx, sy) = syntheticClickFromCorner(corner, screenSize.width, screenSize.height)
                return calculateWindowPositionFromClick(
                    sx, sy, corner,
                    windowWidth, windowHeight,
                    horizontalOffset, verticalOffset,
                )
            }

        return calculateWindowPositionFromClick(
            posToUse.x,
            posToUse.y,
            posToUse.position,
            windowWidth,
            windowHeight,
            horizontalOffset,
            verticalOffset,
        )
    }

    if (getOperatingSystem() == OperatingSystem.MACOS) {
        val (x0, y0) = getStatusItemXYForMac()
        if (x0 != 0 || y0 != 0) {
            TrayClickTracker.setClickPosition(x0, y0, getTrayPosition())
        }
        val pos = TrayClickTracker.getLastClickPosition()
        if (pos != null) {
            return calculateWindowPositionFromClick(
                pos.x,
                pos.y,
                pos.position,
                windowWidth,
                windowHeight,
                horizontalOffset,
                verticalOffset,
            )
        }
    }

    if (getOperatingSystem() == OperatingSystem.LINUX) {
        val clickPos = TrayClickTracker.getLastClickPosition() ?: loadTrayClickPosition()
        if (clickPos != null) {
            return calculateWindowPositionFromClick(
                clickPos.x,
                clickPos.y,
                clickPos.position,
                windowWidth,
                windowHeight,
                horizontalOffset,
                verticalOffset,
            )
        }
    }

    return when (getTrayPosition()) {
        TrayPosition.TOP_LEFT -> WindowPosition(x = (0 + horizontalOffset).dp, y = (0 + verticalOffset).dp)
        TrayPosition.TOP_RIGHT ->
            WindowPosition(
                x = (screenSize.width - windowWidth + horizontalOffset).dp,
                y = (0 + verticalOffset).dp,
            )
        TrayPosition.BOTTOM_LEFT ->
            WindowPosition(
                x = (0 + horizontalOffset).dp,
                y = (screenSize.height - windowHeight + verticalOffset).dp,
            )
        TrayPosition.BOTTOM_RIGHT ->
            WindowPosition(
                x = (screenSize.width - windowWidth + horizontalOffset).dp,
                y = (screenSize.height - windowHeight + verticalOffset).dp,
            )
    }
}

/** Variante par instance (Windows multi-tray, mac precise) + offsets */
fun getTrayWindowPositionForInstance(
    instanceId: String,
    windowWidth: Int,
    windowHeight: Int,
    horizontalOffset: Int = 0,
    verticalOffset: Int = 0,
): WindowPosition {
    val os = getOperatingSystem()

    return when (os) {
        OperatingSystem.WINDOWS -> {
            val pos = TrayClickTracker.getLastClickPosition(instanceId)
            if (pos == null) {
                debugln {
                    "[TrayPosition] getTrayWindowPositionForInstance: " +
                        "no position for $instanceId, using fallback"
                }
                return fallbackCornerPosition(windowWidth, windowHeight, horizontalOffset, verticalOffset)
            }
            calculateWindowPositionFromClick(
                pos.x,
                pos.y,
                pos.position,
                windowWidth,
                windowHeight,
                horizontalOffset,
                verticalOffset,
            )
        }
        OperatingSystem.MACOS -> {
            val trayHandle = MacTrayInitializer.getNativeTrayHandle(instanceId)
            if (trayHandle != 0L) {
                val outXY = IntArray(2)
                val precise =
                    try {
                        MacNativeBridge.nativeGetStatusItemPositionFor(trayHandle, outXY) != 0
                    } catch (_: Throwable) {
                        false
                    }
                val x = outXY[0]
                val y = outXY[1]
                if (precise) {
                    val regionStr = runCatching { MacNativeBridge.nativeGetStatusItemRegionFor(trayHandle) }.getOrNull()
                    val trayPos =
                        if (regionStr != null) {
                            getMacTrayPosition(regionStr)
                        } else {
                            val bounds = getScreenBoundsAt(x, y)
                            convertPositionToCorner(x - bounds.x, y - bounds.y, bounds.width, bounds.height)
                        }
                    TrayClickTracker.setClickPosition(instanceId, x, y, trayPos)
                    return calculateWindowPositionFromClick(
                        x,
                        y,
                        trayPos,
                        windowWidth,
                        windowHeight,
                        horizontalOffset,
                        verticalOffset,
                    )
                }
            }
            // Fallback global
            getTrayWindowPosition(windowWidth, windowHeight, horizontalOffset, verticalOffset)
        }
        else -> getTrayWindowPosition(windowWidth, windowHeight, horizontalOffset, verticalOffset)
    }
}

/**
 * Calcule la position (x,y) depuis un clic précis + applique les offsets et un clamp aux bords écran.
 * Uses the screen containing the click point for correct multi-monitor support.
 */
private fun calculateWindowPositionFromClick(
    clickX: Int,
    clickY: Int,
    trayPosition: TrayPosition,
    windowWidth: Int,
    windowHeight: Int,
    horizontalOffset: Int,
    verticalOffset: Int,
): WindowPosition {
    val os = getOperatingSystem()
    val isTop = trayPosition == TrayPosition.TOP_LEFT || trayPosition == TrayPosition.TOP_RIGHT
    val isRight = trayPosition == TrayPosition.TOP_RIGHT || trayPosition == TrayPosition.BOTTOM_RIGHT

    val sb = getScreenBoundsAt(clickX, clickY)
    debugln {
        "[TrayPosition] calculateWindowPositionFromClick: " +
            "clickX=$clickX, clickY=$clickY, trayPos=$trayPosition, " +
            "winW=$windowWidth, winH=$windowHeight, screenBounds=$sb"
    }

    return if (os == OperatingSystem.WINDOWS) {
        var x = clickX - (windowWidth / 2)
        var y = if (isTop) clickY else clickY - windowHeight
        debugln { "[TrayPosition] Windows: isTop=$isTop, initial x=$x, y=$y" }

        x += horizontalOffset
        y += verticalOffset

        if (x < sb.x) {
            x = sb.x
        } else if (x + windowWidth > sb.x + sb.width) {
            x = sb.x + sb.width - windowWidth
        }
        if (y < sb.y) {
            y = sb.y
        } else if (y + windowHeight > sb.y + sb.height) {
            y = sb.y + sb.height - windowHeight
        }
        debugln { "[TrayPosition] Windows: final x=$x, y=$y" }
        WindowPosition(x = x.dp, y = y.dp)
    } else {
        val panelGuessPx = 28

        var x = clickX - (windowWidth / 2)
        val anchorY = if (isTop) sb.y + panelGuessPx else (sb.y + sb.height - panelGuessPx)
        var y = if (isTop) anchorY else anchorY - windowHeight

        x += if (isRight) -horizontalOffset else horizontalOffset
        y += if (isTop) verticalOffset else -verticalOffset

        if (x < sb.x) {
            x = sb.x
        } else if (x + windowWidth > sb.x + sb.width) {
            x = sb.x + sb.width - windowWidth
        }
        if (y < sb.y) {
            y = sb.y
        } else if (y + windowHeight > sb.y + sb.height) {
            y = sb.y + sb.height - windowHeight
        }
        WindowPosition(x = x.dp, y = y.dp)
    }
}

/** Position de repli coin + offsets */
private fun fallbackCornerPosition(
    w: Int,
    h: Int,
    horizontalOffset: Int,
    verticalOffset: Int,
): WindowPosition {
    val screen = Toolkit.getDefaultToolkit().screenSize
    return when (getTrayPosition()) {
        TrayPosition.TOP_LEFT -> WindowPosition((0 + horizontalOffset).dp, (0 + verticalOffset).dp)
        TrayPosition.TOP_RIGHT -> WindowPosition((screen.width - w + horizontalOffset).dp, (0 + verticalOffset).dp)
        TrayPosition.BOTTOM_LEFT -> WindowPosition((0 + horizontalOffset).dp, (screen.height - h + verticalOffset).dp)
        TrayPosition.BOTTOM_RIGHT ->
            WindowPosition(
                (screen.width - w + horizontalOffset).dp,
                (screen.height - h + verticalOffset).dp,
            )
    }
}

internal fun getMacTrayPosition(nativeResult: String?): TrayPosition =
    when (nativeResult) {
        "top-left" -> TrayPosition.TOP_LEFT
        "top-right" -> TrayPosition.TOP_RIGHT
        else -> TrayPosition.TOP_RIGHT
    }

internal fun getStatusItemXYForMac(): Pair<Int, Int> {
    val outXY = IntArray(2)
    MacNativeBridge.nativeGetStatusItemPosition(outXY) // if not precise, returns (0,0)
    return outXY[0] to outXY[1]
}

fun debugDeleteTrayPropertiesFiles() {
    val files =
        setOfNotNull(
            trayPropertiesFile(),
            legacyPropertiesFile(),
            oldTmpPropertiesFile(),
            macCachePropertiesFile(),
        )
    files.filter(File::exists).forEach { runCatching { it.delete() } }
}

// DPI helpers / hit-test utilities unchanged (kept here for completeness)
private fun dpiAwareHalfIconOffset(): Int {
    return try {
        val dpi = Toolkit.getDefaultToolkit().screenResolution
        val scale = dpi / 96.0
        (15 * scale).roundToInt().coerceAtLeast(0)
    } catch (_: Throwable) {
        15
    }
}

/**
 * Detects the Windows taskbar height based on DPI scaling.
 * Default taskbar: 40px at 100%, 48px at 125%, 60px at 150%, etc.
 */
private fun getWindowsTaskbarHeight(): Int {
    return try {
        val dpi = Toolkit.getDefaultToolkit().screenResolution
        val scale = dpi / 96.0
        // Taskbar default height: 40px at 100% scaling
        (40 * scale).roundToInt().coerceIn(32, 72)
    } catch (_: Throwable) {
        40
    }
}

/**
 * Gets the appropriate bar size (taskbar/menubar/panel) for the current OS.
 */
private fun getSystemBarSize(): Int {
    return when (getOperatingSystem()) {
        OperatingSystem.WINDOWS -> getWindowsTaskbarHeight()
        OperatingSystem.MACOS -> 25 // macOS menu bar
        else -> 28 // Linux panel (GNOME/KDE average)
    }
}

internal fun isPointWithinMacStatusItem(
    px: Int,
    py: Int,
): Boolean {
    if (getOperatingSystem() != OperatingSystem.MACOS) return false
    val (ix, iy) = getStatusItemXYForMac()
    if (ix == 0 && iy == 0) return false
    val dpi = runCatching { Toolkit.getDefaultToolkit().screenResolution }.getOrDefault(96)
    val scale = dpi / 96.0
    val half = (14 * scale).roundToInt().coerceAtLeast(8)
    val left = ix - half
    val right = ix + half
    val top = iy - half
    val bottom = iy + half
    return px in left..right && py in top..bottom
}

internal fun isPointWithinLinuxStatusItem(
    px: Int,
    py: Int,
): Boolean {
    if (getOperatingSystem() != OperatingSystem.LINUX) return false
    val click = TrayClickTracker.getLastClickPosition() ?: loadTrayClickPosition() ?: return false
    val (ix, iy) = click.x to click.y
    val baseIconSizeAt1x =
        when (detectLinuxDesktopEnvironment()) {
            LinuxDesktopEnvironment.KDE -> 22
            LinuxDesktopEnvironment.GNOME -> 24
            LinuxDesktopEnvironment.CINNAMON -> 24
            LinuxDesktopEnvironment.MATE -> 24
            LinuxDesktopEnvironment.XFCE -> 24
            else -> 24
        }
    val dpi = runCatching { Toolkit.getDefaultToolkit().screenResolution }.getOrDefault(96)
    val scale = (dpi / 96.0).coerceAtLeast(0.5)
    val half = (baseIconSizeAt1x * 0.5 * scale).toInt().coerceAtLeast(8)
    val fudge = (4 * scale).toInt().coerceAtLeast(2)
    val left = ix - half - fudge
    val right = ix + half + fudge
    val top = iy - half - fudge
    val bottom = iy + half + fudge
    return px in left..right && py in top..bottom
}

// TrayPosition.kt

private fun syntheticClickFromCorner(
    corner: TrayPosition,
    screenW: Int,
    screenH: Int,
): Pair<Int, Int> {
    val half = dpiAwareHalfIconOffset() // ~half icon in px, DPI-aware
    val x =
        if (corner == TrayPosition.TOP_RIGHT || corner == TrayPosition.BOTTOM_RIGHT) {
            screenW - half
        } else {
            half
        }
    val y =
        if (corner == TrayPosition.BOTTOM_LEFT || corner == TrayPosition.BOTTOM_RIGHT) {
            screenH - half
        } else {
            half
        }
    return x to y
}
