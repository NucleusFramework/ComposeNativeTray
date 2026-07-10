package dev.nucleusframework.composenativetray.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import java.io.File
import java.util.zip.CRC32

/**
 * Utility functions for rendering Composable icons to image files for use in system tray.
 */
object ComposableIconUtils {
    /**
     * Renders a Composable to a PNG file and returns the path to the file.
     *
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return Path to the generated PNG file
     * @throws Exception if rendering fails completely
     */
    fun renderComposableToPngFile(
        iconRenderProperties: IconRenderProperties,
        content: @Composable () -> Unit,
    ): String {
        val tempFile = createTempFile(suffix = ".png")
        val pngData = renderComposableToPngBytes(iconRenderProperties, content)
        tempFile.writeBytes(pngData)
        return tempFile.absolutePath
    }

    /**
     * Renders a Composable to a PNG image and returns the result as a byte array.
     * This function creates an [ImageComposeScene] based on the provided [IconRenderProperties],
     * renders the Composable content, and encodes the output into PNG format.
     * If scaling is required based on the [IconRenderProperties], the rendered content is scaled before encoding.
     *
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return A byte array containing the rendered PNG image data.
     * @throws Exception if rendering fails
     */
    fun renderComposableToPngBytes(
        iconRenderProperties: IconRenderProperties,
        content: @Composable () -> Unit,
    ): ByteArray {
        var scene: ImageComposeScene? = null
        var renderedIcon: Image? = null
        var scaledBitmap: Bitmap? = null
        var scaledImage: Image? = null

        try {
            // Try to create and render the scene
            try {
                scene =
                    ImageComposeScene(
                        width = iconRenderProperties.sceneWidth,
                        height = iconRenderProperties.sceneHeight,
                        density = iconRenderProperties.sceneDensity,
                        coroutineContext = Dispatchers.Unconfined,
                    ) {
                        content()
                    }

                renderedIcon = scene.render()
            } catch (e: Exception) {
                // Log the error but don't modify any system properties
                val errorMessage = e.message ?: "Unknown error"
                errorln { "[ComposableIconUtils] Failed to render scene: $errorMessage" }

                // Check if it's a DirectX error on Windows
                if (errorMessage.contains("DirectX12", ignoreCase = true) ||
                    errorMessage.contains("Failed to choose DirectX12 adapter", ignoreCase = true)
                ) {
                    errorln { "[ComposableIconUtils] DirectX12 not available on this system. Scene rendering failed." }
                }

                // Re-throw the exception - let the caller handle it
                throw e
            }

            val iconData =
                if (iconRenderProperties.requiresScaling) {
                    scaledBitmap =
                        Bitmap().apply {
                            allocN32Pixels(iconRenderProperties.targetWidth, iconRenderProperties.targetHeight)
                        }

                    renderedIcon.scalePixels(
                        scaledBitmap.peekPixels()!!,
                        FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                        true,
                    )

                    scaledImage = Image.makeFromBitmap(scaledBitmap)
                    scaledImage.encodeToData(EncodedImageFormat.PNG)
                        ?: throw Exception("Failed to encode scaled image to PNG")
                } else {
                    renderedIcon.encodeToData(EncodedImageFormat.PNG)
                        ?: throw Exception("Failed to encode image to PNG")
                }

            return iconData.bytes
        } finally {
            // Ensure proper cleanup
            try {
                scaledImage?.close()
                scaledBitmap?.close()
                renderedIcon?.close()
                scene?.close()
            } catch (e: Exception) {
                debugln { "[ComposableIconUtils] Error during cleanup: ${e.message}" }
            }
        }
    }

    /**
     * Renders a Composable to an ICO file and returns the path to the file.
     *
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return Path to the generated ICO file
     * @throws Exception if rendering fails
     */
    fun renderComposableToIcoFile(
        iconRenderProperties: IconRenderProperties,
        content: @Composable (() -> Unit),
    ): String {
        val tempFile = createTempFile(suffix = ".ico")
        val icoData = renderComposableToIcoBytes(iconRenderProperties, content)
        tempFile.writeBytes(icoData)
        return tempFile.absolutePath
    }

    /**
     * Renders a Composable to ICO format bytes.
     * Since ICO format is not directly supported by the encoding library,
     * this method first renders to PNG and then creates a simple ICO wrapper.
     *
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return Byte array containing the ICO data
     * @throws Exception if rendering fails
     */
    fun renderComposableToIcoBytes(
        iconRenderProperties: IconRenderProperties,
        content: @Composable () -> Unit,
    ): ByteArray {
        // First render to PNG format (which is supported)
        val pngBytes = renderComposableToPngBytes(iconRenderProperties, content)

        // Create a simple ICO format wrapper around the PNG data
        // ICO header (6 bytes) + ICO directory entry (16 bytes) + PNG data
        val icoHeaderSize = 6
        val icoDirEntrySize = 16
        val icoData = ByteArray(icoHeaderSize + icoDirEntrySize + pngBytes.size)

        // ICO header
        icoData[0] = 0 // Reserved, must be 0
        icoData[1] = 0 // Reserved, must be 0
        icoData[2] = 1 // Type: 1 for ICO
        icoData[3] = 0 // Type: 1 for ICO (high byte)
        icoData[4] = 1 // Number of images
        icoData[5] = 0 // Number of images (high byte)

        // ICO directory entry
        icoData[6] = iconRenderProperties.targetWidth.toByte() // Width (0 means 256)
        icoData[7] = iconRenderProperties.targetHeight.toByte() // Height (0 means 256)
        icoData[8] = 0 // Color palette size (0 for no palette)
        icoData[9] = 0 // Reserved, must be 0
        icoData[10] = 1 // Color planes
        icoData[11] = 0 // Color planes (high byte)
        icoData[12] = 32 // Bits per pixel
        icoData[13] = 0 // Bits per pixel (high byte)

        // Size of image data in bytes
        val dataSize = pngBytes.size
        icoData[14] = (dataSize and 0xFF).toByte()
        icoData[15] = ((dataSize shr 8) and 0xFF).toByte()
        icoData[16] = ((dataSize shr 16) and 0xFF).toByte()
        icoData[17] = ((dataSize shr 24) and 0xFF).toByte()

        // Offset to image data
        val offset = icoHeaderSize + icoDirEntrySize
        icoData[18] = (offset and 0xFF).toByte()
        icoData[19] = ((offset shr 8) and 0xFF).toByte()
        icoData[20] = ((offset shr 16) and 0xFF).toByte()
        icoData[21] = ((offset shr 24) and 0xFF).toByte()

        // Copy PNG data
        System.arraycopy(pngBytes, 0, icoData, offset, pngBytes.size)

        return icoData
    }

    /**
     * Creates a temporary file that will be deleted when the JVM exits.
     */
    private fun createTempFile(
        prefix: String = "tray_icon_",
        suffix: String,
    ): File {
        val tempFile = File.createTempFile(prefix, suffix)
        tempFile.deleteOnExit()
        return tempFile
    }

    /**
     * Calculates a hash value for the rendered composable content.
     * This can be used to detect changes in the composable content without requiring an explicit key.
     *
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return A hash value representing the current state of the composable content
     */
    @Composable
    fun calculateContentHash(
        iconRenderProperties: IconRenderProperties,
        content: @Composable () -> Unit,
    ): Long {
        return try {
            // Render the composable to PNG bytes
            val pngBytes = renderComposableToPngBytes(iconRenderProperties, content)

            // Calculate CRC32 hash of the PNG bytes
            val crc = CRC32()
            crc.update(pngBytes)
            crc.value
        } catch (e: Exception) {
            errorln { "[ComposableIconUtils] Failed to calculate content hash: ${e.message}" }
            // Return a time-based hash as fallback
            System.currentTimeMillis()
        }
    }
}
