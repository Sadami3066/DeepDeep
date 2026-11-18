package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object MiniappBridgeErrors {
    const val INVALID_REQUEST = "invalid_request"
    const val UNKNOWN_METHOD = "unknown_method"
    const val INVALID_PARAMS = "invalid_params"
    const val DATA_INVALID_KEY = "data_invalid_key"
    const val DATA_TOO_LARGE = "data_too_large"
    const val DATA_CORRUPT = "data_corrupt"
    const val AGENT_UNAVAILABLE = "agent_unavailable"
    const val AGENT_FAILED = "agent_failed"
    const val HOST_UNAVAILABLE = "host_unavailable"
    const val HOST_FAILED = "host_failed"
    const val PERMISSION_DENIED = "permission_denied"
    const val NOT_FOUND = "not_found"
    const val TOO_LARGE = "too_large"
    const val CANCELLED = "cancelled"
}

private const val MAX_DATA_VALUE_CHARS = 256 * 1024
private const val MAX_TOAST_CHARS = 200
private const val MAX_AGENT_PROMPT_CHARS = 64 * 1024
private const val MAX_AGENT_SYSTEM_PROMPT_CHARS = 16 * 1024
private const val MAX_AGENT_TURNS = 64
private const val MAX_NOTIFICATION_TITLE_CHARS = 200
private const val MAX_NOTIFICATION_BODY_CHARS = 1000
private const val MAX_SHARE_TEXT_CHARS = 20 * 1024
private const val MAX_CLIPBOARD_CHARS = 100 * 1024
private const val MAX_SPEECH_CHARS = 8 * 1024
private const val MAX_FILE_PATH_CHARS = 512
private const val MAX_FILE_CONTENT_CHARS = 24 * 1024 * 1024
private const val MAX_REMINDER_HORIZON_MS = 365L * 24 * 60 * 60 * 1000
private const val DEFAULT_SPEECH_TIMEOUT_MS = 15_000L
private const val MIN_SPEECH_TIMEOUT_MS = 3_000L
private const val MAX_SPEECH_TIMEOUT_MS = 60_000L
private const val DEFAULT_CALENDAR_LIMIT = 50
private const val MAX_CALENDAR_LIMIT = 200
private val MINIAPP_KEY_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,63}")

class MiniappBridgeRouter(
    private val manifest: MiniappManifest,
    private val data: MiniappData,
    private val toast: (String) -> Unit,
    private val agent: MiniappAgentGateway? = null,
    private val host: MiniappHostGateway? = null,
) {
    suspend fun handle(rawMessage: String, emit: suspend (JsonObject) -> Unit): String {
        val request = runCatching { Json.parseToJsonElement(rawMessage) }.getOrNull() as? JsonObject
            ?: return errorResponse(null, MiniappBridgeErrors.INVALID_REQUEST, "message must be a JSON object")
        val id = (request["id"] as? JsonPrimitive)
            ?.takeIf { !it.isString && it.contentOrNull != null }
            ?.contentOrNull
            ?.toLongOrNull()
            ?: return errorResponse(null, MiniappBridgeErrors.INVALID_REQUEST, "id must be a number")
        val method = (request["method"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: return errorResponse(id, MiniappBridgeErrors.INVALID_REQUEST, "method must be a string")
        val paramsElement = request["params"]
        if (paramsElement != null && paramsElement !is JsonObject) {
            return errorResponse(id, MiniappBridgeErrors.INVALID_PARAMS, "params must be an object")
        }
        val params = (paramsElement as? JsonObject) ?: JsonObject(emptyMap())
        return try {
            successResponse(id, dispatch(method, params, id, emit))
        } catch (error: BridgeException) {
            errorResponse(id, error.code, error.message ?: error.code)
        } catch (error: CancellationException) {
            throw error
        } catch (error: MiniappHostException) {
            errorResponse(id, error.code, error.message ?: error.code)
        } catch (error: SecurityException) {
            errorResponse(id, MiniappBridgeErrors.PERMISSION_DENIED, error.message ?: "permission denied")
        } catch (error: Exception) {
            errorResponse(id, MiniappBridgeErrors.INVALID_REQUEST, error.message ?: "Miniapp bridge error")
        }
    }

    private suspend fun dispatch(
        method: String,
        params: JsonObject,
        id: Long,
        emit: suspend (JsonObject) -> Unit,
    ): JsonElement = when (method) {
        "info" -> info()
        "data.get" -> dataGet(params)
        "data.set" -> dataSet(params)
        "data.remove" -> dataRemove(params)
        "data.keys" -> dataKeys()
        "ui.toast" -> showToast(params)
        "ui.haptic" -> haptic(params)
        "ui.keepScreenOn" -> keepScreenOn(params)
        "env" -> env()
        "agent.models" -> agentModels()
        "agent.capabilities" -> agentCapabilities()
        "agent.run" -> agentRun(id, params, emit)
        "notify.show" -> notifyShow(params)
        "notify.cancel" -> notifyCancel(params)
        "notify.cancelAll" -> notifyCancelAll()
        "notify.schedule" -> notifySchedule(params)
        "notify.list" -> notifyList()
        "files.pick" -> filesPick(params)
        "files.read" -> filesRead(params)
        "files.write" -> filesWrite(params)
        "files.export" -> filesExport(params)
        "files.url" -> filesUrl(params)
        "files.list" -> filesList(params)
        "files.delete" -> filesDelete(params)
        "share.text" -> shareText(params)
        "share.file" -> shareFile(params)
        "clipboard.read" -> clipboardRead()
        "clipboard.write" -> clipboardWrite(params)
        "media.pick" -> mediaPick(params)
        "media.capture" -> mediaCapture()
        "media.saveToGallery" -> mediaSaveToGallery(params)
        "media.scanQr" -> mediaScanQr()
        "speech.speak" -> speechSpeak(params)
        "speech.stop" -> speechStop()
        "speech.listen" -> speechListen(params)
        "system.openUrl" -> systemOpenUrl(params)
        "location.current" -> locationCurrent()
        "calendar.list" -> calendarList(params)
        "calendar.add" -> calendarAdd(params)
        else -> throw BridgeException(MiniappBridgeErrors.UNKNOWN_METHOD, "Unknown method: $method")
    }

    private fun info(): JsonObject = buildJsonObject {
        putJsonObject("app") {
            put("id", manifest.id)
            put("name", manifest.name)
            put("icon", manifest.icon)
            put("version", manifest.version)
        }
    }

    private suspend fun dataGet(params: JsonObject): JsonElement {
        val key = keyOf(params)
        return dataCall { data.get(key) } ?: JsonNull
    }

    private suspend fun dataSet(params: JsonObject): JsonElement {
        val key = keyOf(params)
        val value = params["value"] ?: throw BridgeException(
            MiniappBridgeErrors.INVALID_PARAMS,
            "value is required",
        )
        if (value.toString().length > MAX_DATA_VALUE_CHARS) {
            throw BridgeException(
                MiniappBridgeErrors.DATA_TOO_LARGE,
                "value must be at most $MAX_DATA_VALUE_CHARS characters",
            )
        }
        dataCall { data.set(key, value) }
        return JsonPrimitive(true)
    }

    private suspend fun dataRemove(params: JsonObject): JsonElement {
        val key = keyOf(params)
        return JsonPrimitive(dataCall { data.remove(key) })
    }

    private suspend fun dataKeys(): JsonElement {
        val keys = dataCall { data.keys() }.sorted()
        return buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } }
    }

    private fun showToast(params: JsonObject): JsonElement {
        val text = (params["text"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
        if (text == null || text.length > MAX_TOAST_CHARS) {
            throw BridgeException(
                MiniappBridgeErrors.INVALID_PARAMS,
                "text must be at most $MAX_TOAST_CHARS characters",
            )
        }
        toast(text)
        return JsonPrimitive(true)
    }

    private suspend fun haptic(params: JsonObject): JsonElement {
        val intensity = optionalString(params, "intensity") ?: "light"
        return JsonPrimitive(hostCall { requireHost().haptic(intensity) })
    }

    private suspend fun keepScreenOn(params: JsonObject): JsonElement {
        val enabled = requiredBoolean(params, "enabled")
        return JsonPrimitive(hostCall { requireHost().keepScreenOn(enabled) })
    }

    private suspend fun env(): JsonElement {
        val gateway = requireHost()
        val value = hostCall { gateway.env() }
        return buildJsonObject {
            put("bridgeVersion", MINIAPP_BRIDGE_VERSION)
            putJsonArray("capabilities") { MINIAPP_CAPABILITIES.forEach { add(JsonPrimitive(it)) } }
            put("locale", value.locale)
            put("fontScale", value.fontScale.toDouble())
            putJsonObject("safeArea") {
                put("top", value.safeArea.top)
                put("bottom", value.safeArea.bottom)
                put("left", value.safeArea.left)
                put("right", value.safeArea.right)
            }
        }
    }

    private fun requireAgent(): MiniappAgentGateway = agent
        ?: throw BridgeException(MiniappBridgeErrors.AGENT_UNAVAILABLE, "miniapp agent is unavailable")

    private fun requireHost(): MiniappHostGateway = host
        ?: throw BridgeException(MiniappBridgeErrors.HOST_UNAVAILABLE, "miniapp host is unavailable")

    private suspend fun <T> hostCall(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: MiniappHostException) {
        throw BridgeException(error.code, error.message ?: error.code)
    } catch (error: SecurityException) {
        throw BridgeException(MiniappBridgeErrors.PERMISSION_DENIED, error.message ?: "permission denied")
    } catch (error: Exception) {
        throw BridgeException(MiniappBridgeErrors.HOST_FAILED, error.message ?: "miniapp host failed")
    }

    private suspend fun notifyShow(params: JsonObject): JsonElement {
        val title = requiredString(params, "title", MAX_NOTIFICATION_TITLE_CHARS)
        val body = optionalString(params, "body", MAX_NOTIFICATION_BODY_CHARS).orEmpty()
        val notificationId = optionalString(params, "id", 64)
        val id = hostCall {
            requireHost().showNotification(
                MiniappNotificationRequest(id = notificationId, title = title, body = body),
            )
        }
        return buildJsonObject { put("id", id) }
    }

    private suspend fun notifyCancel(params: JsonObject): JsonElement {
        val id = requiredString(params, "id", 64)
        return JsonPrimitive(hostCall { requireHost().cancelNotification(id) })
    }

    private suspend fun notifyCancelAll(): JsonElement =
        JsonPrimitive(hostCall { requireHost().cancelNotifications() })

    private suspend fun notifySchedule(params: JsonObject): JsonElement {
        val title = requiredString(params, "title", MAX_NOTIFICATION_TITLE_CHARS)
        val body = optionalString(params, "body", MAX_NOTIFICATION_BODY_CHARS).orEmpty()
        val reminderId = optionalString(params, "id", 64)
        val at = requiredLong(params, "at")
        val now = System.currentTimeMillis()
        if (at < now - 60_000L || at > now + MAX_REMINDER_HORIZON_MS) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "at must be within the next year")
        }
        val repeatDaily = optionalString(params, "repeat")?.equals("daily", ignoreCase = true) ?: false
        val reminder = hostCall {
            requireHost().scheduleReminder(
                MiniappReminderRequest(
                    id = reminderId,
                    title = title,
                    body = body,
                    atMillis = at,
                    repeatDaily = repeatDaily,
                ),
            )
        }
        return reminderJson(reminder)
    }

    private suspend fun notifyList(): JsonElement {
        val reminders = hostCall { requireHost().reminders() }
        return buildJsonArray { reminders.forEach { add(reminderJson(it)) } }
    }

    private fun reminderJson(reminder: MiniappReminder): JsonObject = buildJsonObject {
        put("id", reminder.id)
        put("title", reminder.title)
        put("body", reminder.body)
        put("at", reminder.atMillis)
        put("repeat", if (reminder.repeatDaily) "daily" else "once")
    }

    private suspend fun filesPick(params: JsonObject): JsonElement {
        val types = stringListOf(params, "types").orEmpty().take(8)
        val multiple = optionalBoolean(params, "multiple") ?: false
        val files = hostCall { requireHost().pickFiles(types, multiple) }
        return buildJsonArray { files.forEach { add(fileRefJson(it)) } }
    }

    private suspend fun filesRead(params: JsonObject): JsonElement {
        val path = requiredPath(params)
        val encoding = encodingOf(params)
        val content = hostCall { requireHost().readFile(path, encoding) }
        return buildJsonObject {
            put("path", content.path)
            put("size", content.size)
            put("mimeType", content.mimeType)
            content.text?.let { put("text", it) }
            content.base64?.let { put("base64", it) }
        }
    }

    private suspend fun filesWrite(params: JsonObject): JsonElement {
        val path = requiredPath(params)
        val encoding = encodingOf(params)
        val data = (params["data"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "data must be a string")
        if (data.length > MAX_FILE_CONTENT_CHARS) {
            throw BridgeException(MiniappBridgeErrors.TOO_LARGE, "data is too large")
        }
        val file = hostCall { requireHost().writeFile(path, encoding, data) }
        return fileRefJson(file)
    }

    private suspend fun filesExport(params: JsonObject): JsonElement {
        val path = requiredPath(params)
        return JsonPrimitive(hostCall { requireHost().exportFile(path) })
    }

    private suspend fun filesUrl(params: JsonObject): JsonElement {
        val path = requiredPath(params)
        val url = hostCall { requireHost().fileUrl(path) }
        return buildJsonObject { put("url", url) }
    }

    private suspend fun filesList(params: JsonObject): JsonElement {
        val directory = optionalString(params, "dir", MAX_FILE_PATH_CHARS).orEmpty()
        val entries = hostCall { requireHost().listFiles(directory) }
        return buildJsonArray {
            entries.forEach { entry ->
                add(
                    buildJsonObject {
                        put("name", entry.name)
                        put("path", entry.path)
                        put("size", entry.size)
                        put("directory", entry.directory)
                    },
                )
            }
        }
    }

    private suspend fun filesDelete(params: JsonObject): JsonElement {
        val path = requiredPath(params)
        return JsonPrimitive(hostCall { requireHost().deleteFile(path) })
    }

    private fun fileRefJson(file: MiniappFileRef): JsonObject = buildJsonObject {
        put("name", file.name)
        put("path", file.path)
        put("size", file.size)
        put("mimeType", file.mimeType)
    }

    private suspend fun shareText(params: JsonObject): JsonElement {
        val text = requiredString(params, "text", MAX_SHARE_TEXT_CHARS)
        return JsonPrimitive(hostCall { requireHost().shareText(text) })
    }

    private suspend fun shareFile(params: JsonObject): JsonElement {
        val path = requiredPath(params)
        val mimeType = optionalString(params, "mimeType", 128)
        return JsonPrimitive(hostCall { requireHost().shareFile(path, mimeType) })
    }

    private suspend fun clipboardRead(): JsonElement {
        val text = hostCall { requireHost().readClipboard() }
        return buildJsonObject { if (text == null) put("text", JsonNull) else put("text", text) }
    }

    private suspend fun clipboardWrite(params: JsonObject): JsonElement {
        val text = requiredString(params, "text", MAX_CLIPBOARD_CHARS)
        return JsonPrimitive(hostCall { requireHost().writeClipboard(text) })
    }

    private suspend fun mediaPick(params: JsonObject): JsonElement {
        val type = optionalString(params, "type") ?: "image"
        if (type != "image" && type != "video" && type != "any") {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "type must be image, video or any")
        }
        val multiple = optionalBoolean(params, "multiple") ?: false
        val files = hostCall { requireHost().pickMedia(type, multiple) }
        return buildJsonArray { files.forEach { add(fileRefJson(it)) } }
    }

    private suspend fun mediaCapture(): JsonElement {
        val file = hostCall { requireHost().captureMedia() }
        return file?.let(::fileRefJson) ?: JsonNull
    }

    private suspend fun mediaSaveToGallery(params: JsonObject): JsonElement {
        val path = requiredPath(params)
        return JsonPrimitive(hostCall { requireHost().saveToGallery(path) })
    }

    private suspend fun mediaScanQr(): JsonElement {
        val text = hostCall { requireHost().scanQr() }
        return buildJsonObject { if (text == null) put("text", JsonNull) else put("text", text) }
    }

    private suspend fun speechSpeak(params: JsonObject): JsonElement {
        val text = requiredString(params, "text", MAX_SPEECH_CHARS)
        val language = optionalString(params, "lang", 32)
        return JsonPrimitive(hostCall { requireHost().speak(text, language) })
    }

    private suspend fun speechStop(): JsonElement = JsonPrimitive(hostCall { requireHost().stopSpeaking() })

    private suspend fun speechListen(params: JsonObject): JsonElement {
        val timeout = optionalLong(params, "timeoutMs")?.coerceIn(MIN_SPEECH_TIMEOUT_MS, MAX_SPEECH_TIMEOUT_MS)
            ?: DEFAULT_SPEECH_TIMEOUT_MS
        val text = hostCall { requireHost().listen(timeout) }
        return buildJsonObject { put("text", text) }
    }

    private suspend fun systemOpenUrl(params: JsonObject): JsonElement {
        val url = requiredString(params, "url", 2048)
        return JsonPrimitive(hostCall { requireHost().openUrl(url) })
    }

    private suspend fun locationCurrent(): JsonElement {
        val location = hostCall { requireHost().location() }
        return buildJsonObject {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracyM", location.accuracyM.toDouble())
            put("provider", location.provider)
            put("time", location.timeMillis)
        }
    }

    private suspend fun calendarList(params: JsonObject): JsonElement {
        val now = System.currentTimeMillis()
        val from = optionalLong(params, "from") ?: now
        val to = optionalLong(params, "to") ?: (now + 30L * 24 * 60 * 60 * 1000)
        if (to <= from) throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "to must be after from")
        val limit = (optionalLong(params, "limit")?.toInt() ?: DEFAULT_CALENDAR_LIMIT)
            .coerceIn(1, MAX_CALENDAR_LIMIT)
        val events = hostCall { requireHost().calendarEvents(from, to, limit) }
        return buildJsonArray { events.forEach { add(calendarEventJson(it)) } }
    }

    private suspend fun calendarAdd(params: JsonObject): JsonElement {
        val title = requiredString(params, "title", 200)
        val start = requiredLong(params, "start")
        val end = requiredLong(params, "end")
        if (end <= start) throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "end must be after start")
        val allDay = optionalBoolean(params, "allDay") ?: false
        val notes = optionalString(params, "notes", 4000)
        val location = optionalString(params, "location", 500)
        val event = hostCall {
            requireHost().addCalendarEvent(
                MiniappCalendarDraft(
                    title = title,
                    startMillis = start,
                    endMillis = end,
                    allDay = allDay,
                    description = notes,
                    location = location,
                ),
            )
        }
        return event?.let(::calendarEventJson) ?: JsonNull
    }

    private fun calendarEventJson(event: MiniappCalendarEvent): JsonObject = buildJsonObject {
        put("id", event.id)
        put("calendarId", event.calendarId)
        put("title", event.title)
        put("start", event.startMillis)
        put("end", event.endMillis)
        put("allDay", event.allDay)
        event.description?.let { put("notes", it) }
        event.location?.let { put("location", it) }
    }

    private fun encodingOf(params: JsonObject): String {
        val encoding = optionalString(params, "encoding") ?: "text"
        if (encoding != "text" && encoding != "base64") {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "encoding must be text or base64")
        }
        return encoding
    }

    private fun requiredPath(params: JsonObject): String {
        val path = requiredString(params, "path", MAX_FILE_PATH_CHARS)
        if (path.startsWith("/") || path.split('/').any { it == ".." } || path.contains('\\')) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "path must be a relative path")
        }
        return path
    }

    private fun requiredString(params: JsonObject, field: String, maxChars: Int): String {
        val value = optionalString(params, field)
            ?: throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field is required")
        if (value.length > maxChars) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field must be at most $maxChars characters")
        }
        return value
    }

    private fun optionalString(params: JsonObject, field: String, maxChars: Int = 1024): String? {
        val element = params[field] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field must be a string")
        }
        val value = primitive.content.trim()
        if (value.isEmpty()) return null
        if (value.length > maxChars) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field must be at most $maxChars characters")
        }
        return value
    }

    private fun requiredLong(params: JsonObject, field: String): Long {
        val element = params[field] ?: throw BridgeException(
            MiniappBridgeErrors.INVALID_PARAMS,
            "$field is required",
        )
        return numberLong(element, field)
    }

    private fun optionalLong(params: JsonObject, field: String): Long? {
        val element = params[field] ?: return null
        if (element is JsonNull) return null
        return numberLong(element, field)
    }

    private fun numberLong(element: JsonElement, field: String): Long {
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive.isString) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field must be a number")
        }
        return primitive.longOrNull
            ?: throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field must be a number")
    }

    private fun optionalBoolean(params: JsonObject, field: String): Boolean? {
        val element = params[field] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive.isString) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field must be a boolean")
        }
        return primitive.contentOrNull?.toBooleanStrictOrNull()
            ?: throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field must be a boolean")
    }

    private fun requiredBoolean(params: JsonObject, field: String): Boolean =
        optionalBoolean(params, field)
            ?: throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field is required")

    private suspend fun agentModels(): JsonElement {
        val gateway = requireAgent()
        val models = agentCall { gateway.models() }
        return buildJsonObject {
            putJsonArray("models") {
                models.forEach { model ->
                    add(
                        buildJsonObject {
                            put("providerId", model.providerId)
                            put("modelId", model.modelId)
                            put("label", model.label)
                            putJsonArray("thinkingLevels") {
                                model.thinkingLevels.forEach { level -> add(JsonPrimitive(level)) }
                            }
                        },
                    )
                }
            }
        }
    }

    private suspend fun agentCapabilities(): JsonElement {
        val gateway = requireAgent()
        val capabilities = agentCall { gateway.capabilities() }
        return buildJsonObject {
            putJsonArray("tools") {
                capabilities.tools.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                        },
                    )
                }
            }
            putJsonArray("skills") {
                capabilities.skills.forEach { skill ->
                    add(
                        buildJsonObject {
                            put("name", skill.name)
                            put("description", skill.description)
                        },
                    )
                }
            }
        }
    }

    private suspend fun agentRun(
        id: Long,
        params: JsonObject,
        emit: suspend (JsonObject) -> Unit,
    ): JsonElement {
        val gateway = requireAgent()
        val request = MiniappAgentRequest(
            prompt = promptOf(params),
            providerId = optionalText(params, "providerId"),
            modelId = optionalText(params, "modelId"),
            thinkingLevel = optionalText(params, "thinkingLevel"),
            systemPrompt = systemPromptOf(params),
            maxTurns = maxTurnsOf(params),
            tools = stringListOf(params, "tools"),
            skills = stringListOf(params, "skills"),
        )
        val text = agentCall {
            gateway.run(request) { event -> emit(eventEnvelope(id, event)) }
        }
        return buildJsonObject { put("text", text) }
    }

    private fun eventEnvelope(id: Long, event: MiniappAgentEvent): JsonObject = buildJsonObject {
        put("id", id)
        putJsonObject("event") {
            put("type", event.type)
            event.text?.let { put("text", it) }
            event.name?.let { put("name", it) }
            event.isError?.let { put("isError", it) }
            event.durationMs?.let { put("durationMs", it) }
            event.status?.let { put("status", it) }
            if (event.type == MiniappAgentEventType.RUN_END) put("truncated", event.truncated)
            event.code?.let { put("code", it) }
            event.message?.let { put("message", it) }
        }
    }

    private fun promptOf(params: JsonObject): String {
        val prompt = (requiredText(params, "prompt") ?: throw BridgeException(
            MiniappBridgeErrors.INVALID_PARAMS,
            "prompt is required",
        )).trim()
        if (prompt.isEmpty()) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "prompt must not be empty")
        }
        if (prompt.length > MAX_AGENT_PROMPT_CHARS) {
            throw BridgeException(
                MiniappBridgeErrors.INVALID_PARAMS,
                "prompt must be at most $MAX_AGENT_PROMPT_CHARS characters",
            )
        }
        return prompt
    }

    private fun systemPromptOf(params: JsonObject): String? {
        val systemPrompt = optionalText(params, "systemPrompt") ?: return null
        if (systemPrompt.length > MAX_AGENT_SYSTEM_PROMPT_CHARS) {
            throw BridgeException(
                MiniappBridgeErrors.INVALID_PARAMS,
                "systemPrompt must be at most $MAX_AGENT_SYSTEM_PROMPT_CHARS characters",
            )
        }
        return systemPrompt
    }

    private fun maxTurnsOf(params: JsonObject): Int? {
        val element = params["maxTurns"] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive.isString) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "maxTurns must be an integer")
        }
        val turns = primitive.intOrNull
            ?: throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "maxTurns must be an integer")
        if (turns < 1 || turns > MAX_AGENT_TURNS) {
            throw BridgeException(
                MiniappBridgeErrors.INVALID_PARAMS,
                "maxTurns must be between 1 and $MAX_AGENT_TURNS",
            )
        }
        return turns
    }

    private fun stringListOf(params: JsonObject, field: String): List<String>? {
        val element = params[field] ?: return null
        if (element is JsonNull) return null
        val array = element as? JsonArray ?: throw BridgeException(
            MiniappBridgeErrors.INVALID_PARAMS,
            "$field must be an array of strings",
        )
        return array.map { item ->
            val primitive = item as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field must be an array of strings")
            }
            primitive.content.trim()
        }.filter { it.isNotEmpty() }
    }

    private fun requiredText(params: JsonObject, field: String): String? {
        val element = params[field] ?: return null
        return textOf(element, field)
    }

    private fun optionalText(params: JsonObject, field: String): String? {
        val element = params[field] ?: return null
        return textOf(element, field)
    }

    private fun textOf(element: JsonElement, field: String): String? {
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "$field must be a string")
        }
        return primitive.content.trim().takeIf { it.isNotEmpty() }
    }

    private suspend fun <T> agentCall(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: IllegalArgumentException) {
        throw BridgeException(
            MiniappBridgeErrors.INVALID_PARAMS,
            error.message ?: "invalid miniapp agent request",
        )
    } catch (error: Exception) {
        throw BridgeException(
            MiniappBridgeErrors.AGENT_FAILED,
            error.message ?: "miniapp agent failed",
        )
    }

    private fun keyOf(params: JsonObject): String {
        val key = (params["key"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: throw BridgeException(MiniappBridgeErrors.INVALID_PARAMS, "key must be a string")
        if (!MINIAPP_KEY_REGEX.matches(key)) {
            throw BridgeException(
                MiniappBridgeErrors.DATA_INVALID_KEY,
                "key must match ^[a-z0-9][a-z0-9_-]{0,63}$",
            )
        }
        return key
    }

    private suspend fun <T> dataCall(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw BridgeException(
            MiniappBridgeErrors.DATA_CORRUPT,
            error.message ?: "Miniapp data is unavailable",
        )
    }

    private fun successResponse(id: Long, result: JsonElement): String = buildJsonObject {
        put("id", id)
        put("ok", true)
        put("result", result)
    }.toString()

    private fun errorResponse(id: Long?, code: String, message: String): String = buildJsonObject {
        if (id == null) put("id", JsonNull) else put("id", id)
        put("ok", false)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }.toString()
}

private class BridgeException(val code: String, message: String) : Exception(message)
