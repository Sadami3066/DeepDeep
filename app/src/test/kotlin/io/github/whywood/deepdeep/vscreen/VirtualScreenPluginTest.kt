package io.github.whywood.deepdeep.vscreen

import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.virtualscreen.parseKeyName
import io.github.whywood.deepdeep.plugin.virtualscreen.parseLaunchPackage
import io.github.whywood.deepdeep.plugin.virtualscreen.parseSwipeCommand
import io.github.whywood.deepdeep.plugin.virtualscreen.parseTapCommand
import io.github.whywood.deepdeep.plugin.virtualscreen.parseTextValue
import io.github.whywood.deepdeep.plugin.virtualscreen.virtualScreenAvailability
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VirtualScreenPluginTest {
    @Test
    fun availabilityFollowsBackendAvailability() {
        val reason = "需要无线调试（ADB）或 Root 权限"
        val unavailable = virtualScreenAvailability(available = false, unavailableReason = reason)
        assertTrue(unavailable is PluginAvailability.Unavailable)
        assertEquals(reason, (unavailable as PluginAvailability.Unavailable).reason)
        assertTrue(virtualScreenAvailability(available = true, unavailableReason = reason) is PluginAvailability.Available)
    }

    @Test
    fun tapRequiresCoordinates() {
        expectIllegalArgument { parseTapCommand(buildJsonObject { put("x", 10) }) }
        expectIllegalArgument { parseTapCommand(buildJsonObject { put("y", 10) }) }
    }

    @Test
    fun tapRejectsNegativeCoordinates() {
        expectIllegalArgument {
            parseTapCommand(
                buildJsonObject {
                    put("x", -1)
                    put("y", 2)
                },
            )
        }
    }

    @Test
    fun tapValidatesDurationRange() {
        expectIllegalArgument {
            parseTapCommand(
                buildJsonObject {
                    put("x", 1)
                    put("y", 2)
                    put("durationMs", 4000)
                },
            )
        }
        val command = parseTapCommand(
            buildJsonObject {
                put("x", 1)
                put("y", 2)
            },
        )
        assertEquals(1, command.x)
        assertEquals(2, command.y)
        assertEquals(60L, command.durationMs)
    }

    @Test
    fun swipeValidatesCoordinatesAndDuration() {
        expectIllegalArgument {
            parseSwipeCommand(
                buildJsonObject {
                    put("x1", 1)
                    put("y1", 2)
                    put("x2", 3)
                },
            )
        }
        expectIllegalArgument {
            parseSwipeCommand(
                buildJsonObject {
                    put("x1", 1)
                    put("y1", 2)
                    put("x2", 3)
                    put("y2", 4)
                    put("durationMs", 10)
                },
            )
        }
        val command = parseSwipeCommand(
            buildJsonObject {
                put("x1", 1)
                put("y1", 2)
                put("x2", 3)
                put("y2", 4)
            },
        )
        assertEquals(300L, command.durationMs)
        assertEquals(4, command.y2)
    }

    @Test
    fun launchRequiresValidPackageName() {
        expectIllegalArgument { parseLaunchPackage(buildJsonObject { }) }
        expectIllegalArgument { parseLaunchPackage(buildJsonObject { put("packageName", "settings") }) }
        expectIllegalArgument { parseLaunchPackage(buildJsonObject { put("packageName", "com.android settings") }) }
        assertEquals(
            "com.android.settings",
            parseLaunchPackage(buildJsonObject { put("packageName", " com.android.settings ") }),
        )
    }

    @Test
    fun keyWhitelistIsEnforced() {
        assertEquals("back", parseKeyName(buildJsonObject { put("key", "Back") }))
        expectIllegalArgument { parseKeyName(buildJsonObject { put("key", "power") }) }
        expectIllegalArgument { parseKeyName(buildJsonObject { }) }
    }

    @Test
    fun textRejectsNonAsciiInput() {
        assertEquals("hello", parseTextValue(buildJsonObject { put("text", "hello") }))
        expectIllegalArgument { parseTextValue(buildJsonObject { put("text", "你好") }) }
        expectIllegalArgument { parseTextValue(buildJsonObject { put("text", "") }) }
        expectIllegalArgument { parseTextValue(buildJsonObject { }) }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.isNotEmpty())
        }
    }
}
