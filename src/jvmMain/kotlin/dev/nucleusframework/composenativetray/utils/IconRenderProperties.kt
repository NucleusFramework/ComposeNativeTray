package dev.nucleusframework.composenativetray.utils

import androidx.compose.ui.unit.Density
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem

/**
 * Properties for rendering a Composable icon.
 *
 * @property sceneWidth Width of the [androidx.compose.ui.ImageComposeScene] in pixels
 * @property sceneHeight Height of the [androidx.compose.ui.ImageComposeScene] in pixels
 * @property sceneDensity Density for [androidx.compose.ui.ImageComposeScene]
 * @property targetWidth Width of the rendered icon in pixels
 * @property targetHeight Height of the rendered icon in pixels
 */
data class IconRenderProperties(
    val sceneWidth: Int = 192,
    val sceneHeight: Int = 192,
    val sceneDensity: Density = Density(2f),
    val targetWidth: Int = 192,
    val targetHeight: Int = 192,
) {
    val requiresScaling = sceneWidth != targetWidth || sceneHeight != targetHeight

    companion object {
        /**
         * Provides an [IconRenderProperties] configured for the current operating system.
         *
         * This method determines the rendering size based on the current operating system,
         * defaulting to specific dimensions for Windows, macOS, and Linux. For unsupported operating
         * systems, it defaults to the provided scene width and height.
         *
         * @param sceneWidth Width of the [androidx.compose.ui.ImageComposeScene] in pixels.
         * @param sceneHeight Height of the [androidx.compose.ui.ImageComposeScene] in pixels.
         * @param density Density of the [androidx.compose.ui.ImageComposeScene].
         * @return An instance of [IconRenderProperties] with the appropriate target width and height
         *         based on the operating system.
         */
        fun forCurrentOperatingSystem(
            sceneWidth: Int = 192,
            sceneHeight: Int = 192,
            density: Density = Density(2f),
        ): IconRenderProperties {
            val (targetWidth, targetHeight) =
                when (getOperatingSystem()) {
                    OperatingSystem.WINDOWS -> 32 to 32
                    OperatingSystem.MACOS -> 44 to 44
                    OperatingSystem.LINUX -> 24 to 24
                    else -> sceneWidth to sceneHeight
                }

            return IconRenderProperties(
                sceneWidth = sceneWidth,
                sceneHeight = sceneHeight,
                sceneDensity = density,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
            )
        }

        /**
         * Provides an [IconRenderProperties] configured with settings that don't force icon scaling and aliasing.
         *
         * @param sceneWidth Width of the [androidx.compose.ui.ImageComposeScene] in pixels.
         * @param sceneHeight Height of the [androidx.compose.ui.ImageComposeScene] in pixels.
         * @param density Density of the [androidx.compose.ui.ImageComposeScene].
         * @return An instance of [IconRenderProperties] with the appropriate target width and height based on the operating system.
         */
        fun withoutScalingAndAliasing(
            sceneWidth: Int = 192,
            sceneHeight: Int = 192,
            density: Density = Density(2f),
        ) = IconRenderProperties(
            sceneWidth = sceneWidth,
            sceneHeight = sceneHeight,
            sceneDensity = density,
            targetWidth = sceneWidth,
            targetHeight = sceneHeight,
        )

        /**
         * Provides an [IconRenderProperties] configured for menu items.
         *
         * Menu items typically require smaller icons than tray icons.
         * The default sizes are optimized for each operating system:
         * - Windows: 16x16 pixels (standard menu icon size)
         * - macOS: 16x16 pixels (NSMenu standard)
         * - Linux: 16x16 pixels (GTK menu standard)
         *
         * @param sceneWidth Width of the [androidx.compose.ui.ImageComposeScene] in pixels. Defaults to 64.
         * @param sceneHeight Height of the [androidx.compose.ui.ImageComposeScene] in pixels. Defaults to 64.
         * @param density Density of the [androidx.compose.ui.ImageComposeScene]. Defaults to 2.0 for high-DPI support.
         * @return An instance of [IconRenderProperties] with the appropriate target width and height for menu items.
         */
        fun forMenuItem(
            sceneWidth: Int = 64,
            sceneHeight: Int = 64,
            density: Density = Density(2f),
        ): IconRenderProperties {
            val (targetWidth, targetHeight) =
                when (getOperatingSystem()) {
                    OperatingSystem.WINDOWS -> 16 to 16
                    OperatingSystem.MACOS -> 16 to 16
                    OperatingSystem.LINUX -> 16 to 16
                    else -> 16 to 16
                }

            return IconRenderProperties(
                sceneWidth = sceneWidth,
                sceneHeight = sceneHeight,
                sceneDensity = density,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
            )
        }
    }
}
