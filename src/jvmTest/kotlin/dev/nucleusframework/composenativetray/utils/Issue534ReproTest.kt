package dev.nucleusframework.composenativetray.utils

import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * End-to-end coverage for Nucleus #534 / ComposeNativeTray #426.
 *
 * Windows locks a DLL after `System.load`. The old ComposeNativeTray loader
 * extracted every process to the same path and `Files.move(REPLACE_EXISTING)`
 * onto that lock. Nucleus's content-addressed loader must keep two different
 * WinTray binaries on two distinct cache files so the second JVM never tries
 * to replace the first.
 */
class Issue534ReproTest {
    @Test
    fun `windows denies Files move onto a System load ed WinTray dll`() {
        assertTrue(isWindows, "Issue #534 is a Windows DLL-lock bug")
        val published = publishedWinTray()

        val dir = Files.createTempDirectory("cnt-534-lock-").toFile()
        try {
            val locked = File(dir, "WinTray.dll")
            published.copyTo(locked, overwrite = true)
            System.load(locked.absolutePath)

            val incoming = File(dir, "WinTray.dll.tmp")
            incoming.writeBytes(ByteArray(64) { 0x5A })

            val thrown =
                runCatching {
                    Files.move(incoming.toPath(), locked.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }.exceptionOrNull()

            assertIs<AccessDeniedException>(
                thrown,
                "Windows must deny replacing a loaded DLL (the old loader did this)",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `second jvm with a different WinTray binary must not throw AccessDenied`() {
        assertTrue(isWindows, "Issue #534 is a Windows DLL-lock bug")

        val java =
            ProcessHandle.current().info().command().orElseThrow {
                IllegalStateException("Cannot resolve the current java executable")
            }
        val classpath = System.getProperty("java.class.path")
        val main = Issue534ReproMain::class.java.name
        val published = publishedWinTray()

        val fakeRoot = Files.createTempDirectory("cnt-534-fake-").toFile()
        val stopFile = File(fakeRoot, "stop-holder")
        try {
            val fakeDll = File(fakeRoot, "composetray/native/win32-x64/WinTray.dll")
            fakeDll.parentFile.mkdirs()
            // Overlay keeps the PE loadable but changes the Nucleus fingerprint
            // so the second JVM extracts to a different cache directory.
            fakeDll.writeBytes(published.readBytes() + byteArrayOf(0x00, 0x53, 0x34))

            val holder =
                ProcessBuilder(
                    java,
                    "-cp",
                    classpath,
                    main,
                    "--hold",
                    "--stop-file=${stopFile.absolutePath}",
                ).redirectErrorStream(true).start()
            try {
                check(waitForLine(holder, "HOLDING")) { "holder did not reach HOLDING" }

                val second =
                    ProcessBuilder(
                        java,
                        "-cp",
                        fakeRoot.absolutePath + File.pathSeparator + classpath,
                        main,
                        "--extract",
                    ).redirectErrorStream(true).start()

                val finished = second.waitFor(30, TimeUnit.SECONDS)
                val output = second.inputStream.bufferedReader().readText()
                if (!finished) {
                    second.destroyForcibly()
                    fail("second JVM timed out.\n$output")
                }

                assertFalse(
                    output.contains("AccessDeniedException"),
                    "second JVM hit AccessDeniedException while loading a different WinTray.dll:\n$output",
                )
                assertEquals(0, second.exitValue(), "second JVM exited ${second.exitValue()}:\n$output")
                assertTrue(output.contains("LOAD_OK=true"), "second JVM did not load WinTray:\n$output")
            } finally {
                stopFile.writeText("stop")
                if (!holder.waitFor(5, TimeUnit.SECONDS)) {
                    ProcessBuilder("taskkill", "/F", "/T", "/PID", holder.pid().toString())
                        .inheritIO()
                        .start()
                        .waitFor(5, TimeUnit.SECONDS)
                    holder.waitFor(5, TimeUnit.SECONDS)
                }
            }
        } finally {
            fakeRoot.deleteRecursively()
        }
    }

    private fun publishedWinTray(): File {
        val published = File("src/jvmMain/resources/composetray/native/win32-x64/WinTray.dll")
        require(published.isFile) { "missing published WinTray.dll at $published" }
        return published
    }

    private fun waitForLine(
        process: Process,
        token: String,
    ): Boolean {
        val reader = process.inputStream.bufferedReader()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline && process.isAlive) {
            if (reader.ready()) {
                val line = reader.readLine() ?: break
                if (line.contains(token)) return true
            } else {
                Thread.sleep(50)
            }
        }
        return false
    }

    private val isWindows: Boolean
        get() = System.getProperty("os.name").orEmpty().lowercase().contains("win")
}
