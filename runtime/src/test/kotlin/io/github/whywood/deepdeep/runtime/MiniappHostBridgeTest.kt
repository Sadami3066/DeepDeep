package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniappHostBridgeTest {
    @Test
    fun envReturnsBridgeVersionCapabilitiesAndSafeArea() = runTest {
        val host = FakeHost()
        val bridge = Bridge(host)

        val response = bridge.call("env")

        assertTrue(response.bool("ok"))
        val result = response.getValue("result").jsonObject
        assertEquals(MINIAPP_BRIDGE_VERSION, result.getValue("bridgeVersion").jsonPrimitive.content.toInt())
        val capabilities = result.getValue("capabilities").jsonArray.map { it.jsonPrimitive.content }
        assertTrue(capabilities.contains("notify.show"))
        assertTrue(capabilities.contains("files.read"))
        assertTrue(capabilities.contains("calendar.add"))
        val safeArea = result.getValue("safeArea").jsonObject
        assertEquals(24, safeArea.getValue("top").jsonPrimitive.content.toInt())
        assertEquals(48, safeArea.getValue("bottom").jsonPrimitive.content.toInt())
    }

    @Test
    fun notifyShowPassesRequestAndReturnsId() = runTest {
        val host = FakeHost()
        val bridge = Bridge(host)

        val response = bridge.call(
            "notify.show",
            buildJsonObject {
                put("title", "喝水")
                put("body", "该喝水了")
                put("id", "water")
            },
        )

        assertTrue(response.bool("ok"))
        assertEquals("water", response.getValue("result").jsonObject.text("id"))
        assertEquals("喝水", host.notification?.title)
        assertEquals("该喝水了", host.notification?.body)
        assertEquals("water", host.notification?.id)
    }

    @Test
    fun notifyShowRequiresTitle() = runTest {
        val bridge = Bridge(FakeHost())

        val response = bridge.call("notify.show", buildJsonObject { put("body", "缺标题") })

        assertFalse(response.bool("ok"))
        assertEquals("invalid_params", response.errorCode())
    }

    @Test
    fun notifyScheduleRejectsTimesOutsideHorizon() = runTest {
        val bridge = Bridge(FakeHost())

        val past = bridge.call(
            "notify.schedule",
            buildJsonObject {
                put("title", "提醒")
                put("at", 1_000L)
                put("body", "过去时间")
            },
        )
        assertEquals("invalid_params", past.errorCode())

        val farFuture = bridge.call(
            "notify.schedule",
            buildJsonObject {
                put("title", "提醒")
                put("at", System.currentTimeMillis() + 400L * 24 * 60 * 60 * 1000)
                put("body", "太远了")
            },
        )
        assertEquals("invalid_params", farFuture.errorCode())
    }

    @Test
    fun filesReadRejectsAbsolutePath() = runTest {
        val bridge = Bridge(FakeHost())

        val response = bridge.call("files.read", buildJsonObject { put("path", "/etc/passwd") })

        assertFalse(response.bool("ok"))
        assertEquals("invalid_params", response.errorCode())
    }

    @Test
    fun filesWriteForwardsEncodingAndData() = runTest {
        val host = FakeHost()
        val bridge = Bridge(host)

        val response = bridge.call(
            "files.write",
            buildJsonObject {
                put("path", "notes/today.txt")
                put("data", "hello")
                put("encoding", "text")
            },
        )

        assertTrue(response.bool("ok"))
        assertEquals("notes/today.txt", host.writtenPath)
        assertEquals("text", host.writtenEncoding)
        assertEquals("hello", host.writtenData)
        assertEquals("notes/today.txt", response.getValue("result").jsonObject.text("path"))
    }

    @Test
    fun clipboardReadReturnsText() = runTest {
        val host = FakeHost()
        val bridge = Bridge(host)

        val response = bridge.call("clipboard.read")

        assertTrue(response.bool("ok"))
        assertEquals("copied", response.getValue("result").jsonObject.text("text"))
    }

    @Test
    fun permissionDeniedMapsToErrorCode() = runTest {
        val host = FakeHost()
        host.failure = MiniappHostException(MiniappHostException.PERMISSION_DENIED, "denied")
        val bridge = Bridge(host)

        val response = bridge.call("location.current")

        assertFalse(response.bool("ok"))
        assertEquals("permission_denied", response.errorCode())
    }

    @Test
    fun hostMissingReturnsHostUnavailable() = runTest {
        val bridge = Bridge(host = null)

        val response = bridge.call("env")

        assertFalse(response.bool("ok"))
        assertEquals("host_unavailable", response.errorCode())
    }

    @Test
    fun calendarAddRequiresEndAfterStart() = runTest {
        val bridge = Bridge(FakeHost())

        val response = bridge.call(
            "calendar.add",
            buildJsonObject {
                put("title", "考试")
                put("start", 2_000L)
                put("end", 1_000L)
            },
        )

        assertFalse(response.bool("ok"))
        assertEquals("invalid_params", response.errorCode())
    }

    @Test
    fun shareTextIsForwarded() = runTest {
        val host = FakeHost()
        val bridge = Bridge(host)

        val response = bridge.call("share.text", buildJsonObject { put("text", "分享内容") })

        assertTrue(response.bool("ok"))
        assertEquals("分享内容", host.sharedText)
    }

    private class Bridge(host: MiniappHostGateway?) {
        private val router = MiniappBridgeRouter(
            MiniappManifest(id = "garden", name = "Garden", icon = "🌱", version = "1.0.0"),
            FakeData(),
            {},
            null,
            host,
        )

        suspend fun call(method: String, params: JsonObject? = null): JsonObject {
            val request = buildJsonObject {
                put("id", 1)
                put("method", method)
                if (params != null) put("params", params)
            }
            return Json.parseToJsonElement(router.handle(request.toString(), {})).jsonObject
        }
    }

    private abstract class FakeHostBase : MiniappHostGateway {
        override suspend fun env(): MiniappEnv = MiniappEnv(
            locale = "zh-CN",
            fontScale = 1.0f,
            safeArea = MiniappSafeArea(top = 24, bottom = 48, left = 0, right = 0),
        )

        override suspend fun showNotification(request: MiniappNotificationRequest): String = "notification"

        override suspend fun cancelNotification(id: String): Boolean = false

        override suspend fun cancelNotifications(): Boolean = false

        override suspend fun scheduleReminder(request: MiniappReminderRequest): MiniappReminder =
            MiniappReminder(request.id ?: "reminder", request.title, request.body, request.atMillis, request.repeatDaily)

        override suspend fun cancelReminder(id: String): Boolean = false

        override suspend fun reminders(): List<MiniappReminder> = emptyList()

        override suspend fun pickFiles(types: List<String>, multiple: Boolean): List<MiniappFileRef> = emptyList()

        override suspend fun readFile(path: String, encoding: String): MiniappFileRead =
            MiniappFileRead(path, 0, "text/plain", "", null)

        override suspend fun writeFile(path: String, encoding: String, data: String): MiniappFileRef =
            MiniappFileRef(path.substringAfterLast('/'), path, data.length.toLong(), "text/plain")

        override suspend fun exportFile(path: String): Boolean = false

        override suspend fun deleteFile(path: String): Boolean = false

        override suspend fun listFiles(directory: String): List<MiniappFileEntry> = emptyList()

        override suspend fun fileUrl(path: String): String = "/__files/$path"

        override suspend fun shareText(text: String): Boolean = true

        override suspend fun shareFile(path: String, mimeType: String?): Boolean = true

        override suspend fun readClipboard(): String? = null

        override suspend fun writeClipboard(text: String): Boolean = true

        override suspend fun haptic(intensity: String): Boolean = true

        override suspend fun keepScreenOn(enabled: Boolean): Boolean = true

        override suspend fun pickMedia(type: String, multiple: Boolean): List<MiniappFileRef> = emptyList()

        override suspend fun captureMedia(): MiniappFileRef? = null

        override suspend fun saveToGallery(path: String): Boolean = false

        override suspend fun scanQr(): String? = null

        override suspend fun speak(text: String, language: String?): Boolean = true

        override suspend fun stopSpeaking(): Boolean = true

        override suspend fun listen(timeoutMillis: Long): String = ""

        override suspend fun openUrl(url: String): Boolean = true

        override suspend fun location(): MiniappLocation =
            MiniappLocation(0.0, 0.0, 0f, "network", 0L)

        override suspend fun calendarEvents(
            fromMillis: Long,
            toMillis: Long,
            limit: Int,
        ): List<MiniappCalendarEvent> = emptyList()

        override suspend fun addCalendarEvent(draft: MiniappCalendarDraft): MiniappCalendarEvent? = null
    }

    private class FakeHost : FakeHostBase() {
        var notification: MiniappNotificationRequest? = null
        var writtenPath: String? = null
        var writtenEncoding: String? = null
        var writtenData: String? = null
        var sharedText: String? = null
        var failure: Exception? = null

        override suspend fun showNotification(request: MiniappNotificationRequest): String {
            notification = request
            return request.id ?: "generated"
        }

        override suspend fun writeFile(path: String, encoding: String, data: String): MiniappFileRef {
            writtenPath = path
            writtenEncoding = encoding
            writtenData = data
            return MiniappFileRef(path.substringAfterLast('/'), path, data.length.toLong(), "text/plain")
        }

        override suspend fun readClipboard(): String = "copied"

        override suspend fun shareText(text: String): Boolean {
            sharedText = text
            return true
        }

        override suspend fun location(): MiniappLocation {
            failure?.let { throw it }
            return super.location()
        }
    }

    private class FakeData : MiniappData {
        private val values = mutableMapOf<String, JsonElement>()

        override suspend fun get(key: String): JsonElement? = values[key]

        override suspend fun set(key: String, value: JsonElement) {
            values[key] = value
        }

        override suspend fun remove(key: String): Boolean = values.remove(key) != null

        override suspend fun keys(): List<String> = values.keys.toList()

        override fun close() = Unit
    }

    private fun JsonObject.bool(field: String): Boolean = getValue(field).jsonPrimitive.content.toBoolean()

    private fun JsonObject.text(field: String): String = getValue(field).jsonPrimitive.content

    private fun JsonObject.errorCode(): String =
        getValue("error").jsonObject.getValue("code").jsonPrimitive.content
}
