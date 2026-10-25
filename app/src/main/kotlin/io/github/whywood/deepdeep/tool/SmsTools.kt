package io.github.whywood.deepdeep.tool

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.Telephony
import androidx.core.content.ContextCompat
import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import io.github.whywood.deepdeep.R
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

const val READ_SMS_TOOL_NAME = "read_sms"
const val SMS_BODY_LIMIT = 2000
const val SMS_DEFAULT_LIMIT = 10
const val SMS_MIN_LIMIT = 1
const val SMS_MAX_LIMIT = 50

private const val COLUMN_ID = "_id"
private const val COLUMN_ADDRESS = "address"
private const val COLUMN_BODY = "body"
private const val COLUMN_DATE = "date"
private const val COLUMN_TYPE = "type"
private const val COLUMN_READ = "read"
private val SMS_PROJECTION = arrayOf(COLUMN_ID, COLUMN_ADDRESS, COLUMN_BODY, COLUMN_DATE, COLUMN_TYPE, COLUMN_READ)

enum class SmsBox {
    INBOX,
    SENT,
    ALL,
}

data class SmsMessage(
    val address: String,
    val date: Long,
    val read: Boolean,
    val body: String,
)

interface SmsReader {
    suspend fun query(box: SmsBox, limit: Int, address: String?, since: Long?): List<SmsMessage>
}

class AndroidSmsReader(private val resolver: ContentResolver) : SmsReader {
    override suspend fun query(
        box: SmsBox,
        limit: Int,
        address: String?,
        since: Long?,
    ): List<SmsMessage> = withContext(Dispatchers.IO) {
        val clauses = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        val uri = when (box) {
            SmsBox.INBOX -> Telephony.Sms.Inbox.CONTENT_URI
            SmsBox.SENT -> Telephony.Sms.Sent.CONTENT_URI
            SmsBox.ALL -> Telephony.Sms.CONTENT_URI
        }
        if (address != null) {
            clauses += "$COLUMN_ADDRESS = ?"
            arguments += address
        }
        if (since != null) {
            clauses += "$COLUMN_DATE >= ?"
            arguments += since.toString()
        }
        resolver.query(
            uri,
            SMS_PROJECTION,
            clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            arguments.takeIf { it.isNotEmpty() }?.toTypedArray(),
            "$COLUMN_DATE DESC LIMIT $limit",
        )?.use { rows ->
            val addressIndex = rows.getColumnIndex(COLUMN_ADDRESS)
            val bodyIndex = rows.getColumnIndex(COLUMN_BODY)
            val dateIndex = rows.getColumnIndex(COLUMN_DATE)
            val readIndex = rows.getColumnIndex(COLUMN_READ)
            buildList {
                while (size < limit && rows.moveToNext()) {
                    add(
                        SmsMessage(
                            address = rows.stringOrEmpty(addressIndex),
                            date = rows.longOrZero(dateIndex),
                            read = rows.intOrZero(readIndex) != 0,
                            body = rows.stringOrEmpty(bodyIndex),
                        ),
                    )
                }
            }
        }.orEmpty()
    }
}

data class SmsToolErrors(
    val disabled: String = "Reading SMS is turned off. Enable Read SMS in Settings first.",
    val permissionMissing: String = "The SMS permission is missing. Enable Read SMS in Settings first.",
    val queryFailed: (String) -> String = { reason -> "Could not read SMS: $reason" },
)

fun smsToolErrors(context: Context): SmsToolErrors {
    val appContext = context.applicationContext
    return SmsToolErrors(
        disabled = appContext.getString(R.string.sms_tool_error_disabled),
        permissionMissing = appContext.getString(R.string.sms_tool_error_permission),
        queryFailed = { reason -> appContext.getString(R.string.sms_tool_error_query, reason) },
    )
}

fun hasReadSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

fun readSmsTool(
    reader: SmsReader,
    enabled: suspend () -> Boolean,
    permissionCheck: () -> Boolean,
    errors: SmsToolErrors = SmsToolErrors(),
): CodingTool = CodingTool(
    name = READ_SMS_TOOL_NAME,
    description = "Read text messages (SMS) from this phone. The tool is read-only and never sends, deletes or modifies messages. The user must enable Read SMS in Settings and grant the SMS permission.",
    parameters = readSmsParameters,
    label = READ_SMS_TOOL_NAME,
    promptSnippet = "Read SMS messages",
    promptGuidelines = listOf(
        "Use read_sms only when the user asks about their text messages.",
        "Messages are read-only: never claim to send, delete or change them.",
        "If the tool reports that reading is off or the permission is missing, ask the user to enable Read SMS in Settings.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        if (!enabled()) {
            textResult(errors.disabled, isError = true)
        } else if (!permissionCheck()) {
            textResult(errors.permissionMissing, isError = true)
        } else {
            querySmsResult(reader, args, errors)
        }
    },
)

internal fun normalizeSmsBody(body: String): String =
    body.replace("\r\n", "\n").replace('\r', '\n').take(SMS_BODY_LIMIT)

private suspend fun querySmsResult(
    reader: SmsReader,
    args: JsonObject,
    errors: SmsToolErrors,
): AgentToolResult = try {
    val request = parseSmsRequest(args)
    val messages = reader.query(request.box, request.limit, request.address, request.since)
        .take(request.limit)
    textResult(messagesJson(messages).toString())
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: SecurityException) {
    textResult(errors.permissionMissing, isError = true)
} catch (error: IllegalArgumentException) {
    textResult(error.message ?: "invalid read_sms arguments", isError = true)
} catch (error: Exception) {
    textResult(errors.queryFailed(error.message.orEmpty()), isError = true)
}

private data class SmsRequest(
    val box: SmsBox,
    val limit: Int,
    val address: String?,
    val since: Long?,
)

private fun parseSmsRequest(args: JsonObject): SmsRequest {
    val limit = if (args.containsKey("limit")) {
        args.intOrNull("limit") ?: throw IllegalArgumentException("limit must be an integer")
    } else {
        SMS_DEFAULT_LIMIT
    }
    require(limit in SMS_MIN_LIMIT..SMS_MAX_LIMIT) {
        "limit must be between $SMS_MIN_LIMIT and $SMS_MAX_LIMIT"
    }
    val box = when (args.stringOrNull("box")?.trim()?.lowercase(Locale.US)) {
        null, "" -> SmsBox.INBOX
        "inbox" -> SmsBox.INBOX
        "sent" -> SmsBox.SENT
        "all" -> SmsBox.ALL
        else -> throw IllegalArgumentException("box must be inbox, sent or all")
    }
    val address = args.stringOrNull("address")?.trim()?.takeIf { it.isNotEmpty() }
    val since = if (args.containsKey("since")) {
        args.longOrNull("since")?.also {
            require(it >= 0) { "since must be a non-negative epoch millisecond value" }
        } ?: throw IllegalArgumentException("since must be an integer")
    } else {
        null
    }
    return SmsRequest(box, limit, address, since)
}

private fun messagesJson(messages: List<SmsMessage>): JsonArray = buildJsonArray {
    messages.forEach { message ->
        add(
            buildJsonObject {
                put("address", message.address)
                put("date", message.date)
                put("read", message.read)
                put("body", normalizeSmsBody(message.body))
            },
        )
    }
}

private val readSmsParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("limit") {
            put("type", "integer")
            put("minimum", SMS_MIN_LIMIT)
            put("maximum", SMS_MAX_LIMIT)
            put("default", SMS_DEFAULT_LIMIT)
            put("description", "Number of messages to return, $SMS_MIN_LIMIT to $SMS_MAX_LIMIT, default $SMS_DEFAULT_LIMIT")
        }
        putJsonObject("box") {
            put("type", "string")
            put("enum", buildJsonArray {
                add(JsonPrimitive("inbox"))
                add(JsonPrimitive("sent"))
                add(JsonPrimitive("all"))
            })
            put("description", "Which mailbox to read: inbox (received), sent (sent by the user) or all, default inbox")
        }
        putJsonObject("address") {
            put("type", "string")
            put("description", "Only messages whose phone number exactly matches this value")
        }
        putJsonObject("since") {
            put("type", "integer")
            put("description", "Only messages at or after this time, in epoch milliseconds")
        }
    }
}

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

private fun JsonObject.longOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

private fun Cursor.stringOrEmpty(index: Int): String =
    if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""

private fun Cursor.longOrZero(index: Int): Long =
    if (index >= 0 && !isNull(index)) getLong(index) else 0L

private fun Cursor.intOrZero(index: Int): Int =
    if (index >= 0 && !isNull(index)) getInt(index) else 0
