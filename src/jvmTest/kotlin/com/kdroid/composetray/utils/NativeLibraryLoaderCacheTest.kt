package com.kdroid.composetray.utils

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for issue #401.
 *
 * The AVX/SSE2 builds of WinTray.dll are byte-for-byte the same size (23552 bytes)
 * but differ in content. The old loader validated its persistent cache by size only,
 * so after upgrading the user kept running the stale AVX library and the crash
 * survived the fix. The cache must be validated by content hash instead.
 */
class NativeLibraryLoaderCacheTest {
    private fun tempFileWith(bytes: ByteArray): File =
        File.createTempFile("composetray-cache-test", ".bin").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    @Test
    fun `stale cache of identical size but different content is rejected`() {
        // Two payloads with the SAME size but DIFFERENT content — the exact
        // shape of the AVX vs SSE2 WinTray.dll pair.
        val cachedBytes = ByteArray(23552) { 0x01 }
        val resourceBytes = ByteArray(23552) { 0x02 }
        require(cachedBytes.size == resourceBytes.size)

        val cachedFile = tempFileWith(cachedBytes)
        val resourceHash = resourceBytes.inputStream().use { it.sha256() }

        // A size-only check would have accepted this stale file; the hash check rejects it.
        assertFalse(
            NativeLibraryLoader.isCacheUpToDate(cachedFile, resourceHash),
            "Same-size but different-content cache must be treated as stale and re-extracted",
        )
    }

    @Test
    fun `matching content is served from cache`() {
        val bytes = ByteArray(23552) { (it % 256).toByte() }
        val cachedFile = tempFileWith(bytes)
        val resourceHash = bytes.inputStream().use { it.sha256() }

        assertTrue(
            NativeLibraryLoader.isCacheUpToDate(cachedFile, resourceHash),
            "Identical content must be reused from cache",
        )
    }

    @Test
    fun `missing cache file is not up to date`() {
        val missing = File.createTempFile("composetray-cache-test", ".bin").apply { delete() }
        assertFalse(NativeLibraryLoader.isCacheUpToDate(missing, "anyhash"))
    }
}
