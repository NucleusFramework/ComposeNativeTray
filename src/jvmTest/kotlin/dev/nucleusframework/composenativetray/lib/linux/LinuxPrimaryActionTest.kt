package dev.nucleusframework.composenativetray.lib.linux

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Run on Linux with busctl installed:
 * `dbus-run-session -- env COMPOSE_TRAY_DBUS_TEST=1 ./gradlew :jvmTest --tests '*LinuxPrimaryActionTest'`
 */
class LinuxPrimaryActionTest {
    @Test
    fun `manager synchronizes primary action presence and replacement`() {
        if (System.getenv("COMPOSE_TRAY_DBUS_TEST") != "1") return
        val calls = AtomicInteger()
        val tray = LinuxTrayManager("primary-action-test", "", "test")
        tray.startTray()
        try {
            awaitProperty("b true")
            activate()
            assertEquals(0, calls.get())

            tray.update("", "test", { calls.incrementAndGet() }, null)
            awaitProperty("b false")
            activate()
            awaitCalls(calls, 1)

            tray.update("", "test", { calls.addAndGet(10) }, null)
            activate()
            awaitCalls(calls, 11)

            tray.update("", "test", null, null)
            awaitProperty("b true")
            activate()
            assertEquals(11, calls.get())
        } finally {
            tray.stopTray()
        }

        val initialActionTray =
            LinuxTrayManager("initial-action-test", "", "test", { calls.incrementAndGet() })
        initialActionTray.startTray()
        try {
            awaitProperty("b false")
            activate()
            awaitCalls(calls, 12)
        } finally {
            initialActionTray.stopTray()
        }
    }

    private fun awaitCalls(
        calls: AtomicInteger,
        expected: Int,
    ) {
        repeat(200) {
            if (calls.get() == expected) return
            Thread.sleep(10)
        }
        assertEquals(expected, calls.get())
    }

    private fun awaitProperty(expected: String) {
        var output = ""
        repeat(200) {
            output = busctl("get-property", service, path, iface, "ItemIsMenu")
            if (output == expected) return
            Thread.sleep(10)
        }
        assertEquals(expected, output)
    }

    private fun activate() {
        assertEquals("", busctl("call", service, path, iface, "Activate", "ii", "12", "34"))
        // Activate's D-Bus reply can arrive before the queued JVM callback.
        Thread.sleep(50)
    }

    private fun busctl(vararg args: String): String {
        val process =
            ProcessBuilder(listOf("busctl", "--user", "--timeout=2") + args).redirectErrorStream(true).start()
        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "busctl timed out")
        return process.inputStream.bufferedReader().readText().trim()
    }

    private val service = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
    private val path = "/StatusNotifierItem"
    private val iface = "org.kde.StatusNotifierItem"
}
