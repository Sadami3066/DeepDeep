package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import io.github.whywood.deepdeep.plugin.api.PluginSmsMessage
import io.github.whywood.deepdeep.plugin.api.PluginSmsThread
import io.github.whywood.deepdeep.plugin.api.SmsService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SmsRow(
    val threadId: String,
    val address: String,
    val body: String,
    val dateMs: Long,
    val incoming: Boolean,
    val read: Boolean,
)

internal fun splitSmsText(text: String, singleLimit: Int = SMS_SINGLE_LIMIT, multipartLimit: Int = SMS_PART_LIMIT): List<String> {
    if (text.isEmpty()) return emptyList()
    if (text.length <= singleLimit) return listOf(text)
    val limit = multipartLimit.coerceAtLeast(1)
    return text.chunked(limit)
}

internal fun smsMessage(row: SmsRow): PluginSmsMessage = PluginSmsMessage(
    id = "",
    threadId = row.threadId,
    address = row.address,
    body = row.body,
    dateMs = row.dateMs,
    incoming = row.incoming,
    read = row.read,
)

internal fun smsThreads(rows: List<SmsRow>, limit: Int): List<PluginSmsThread> {
    val grouped = LinkedHashMap<String, MutableList<SmsRow>>()
    rows.forEach { row -> grouped.getOrPut(row.threadId) { mutableListOf() } += row }
    return grouped.entries
        .map { (threadId, messages) ->
            val latest = messages.maxByOrNull { it.dateMs } ?: messages.first()
            PluginSmsThread(
                id = threadId,
                address = latest.address,
                snippet = latest.body.take(SMS_SNIPPET_LENGTH).takeIf { it.isNotBlank() },
                dateMs = latest.dateMs,
                unreadCount = messages.count { it.incoming && !it.read },
            )
        }
        .sortedByDescending { it.dateMs }
        .let { threads -> if (limit > 0) threads.take(limit) else threads }
}

internal class SmsPluginServiceImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SmsService {
    private val resolver = context.contentResolver

    override val available: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
            runCatching { smsManagerFor(null) }.getOrNull() != null

    override val canRead: Boolean
        get() = DataPermissions.granted(context, Manifest.permission.READ_SMS)

    override val canSend: Boolean
        get() = DataPermissions.granted(context, Manifest.permission.SEND_SMS)

    override suspend fun threads(limit: Int): List<PluginSmsThread> = withContext(ioDispatcher) {
        requireRead()
        val rows = queryRows(threadId = null, address = null, limit = THREAD_SCAN_LIMIT)
        smsThreads(rows, limit)
    }

    override suspend fun messages(threadId: String?, address: String?, limit: Int): List<PluginSmsMessage> =
        withContext(ioDispatcher) {
            requireRead()
            queryRows(threadId = threadId, address = address, limit = limit).map { row ->
                PluginSmsMessage(
                    id = "",
                    threadId = row.threadId,
                    address = row.address,
                    body = row.body,
                    dateMs = row.dateMs,
                    incoming = row.incoming,
                    read = row.read,
                )
            }
        }

    override suspend fun send(address: String, text: String, subscriptionId: Int?): Boolean =
        withContext(ioDispatcher) {
            if (!canSend) {
                throw SecurityException(
                    "SMS sending requires SEND_SMS permission; grant SMS access for DeepDeep and retry",
                )
            }
            val target = address.trim()
            require(target.isNotEmpty()) { "SMS address must not be empty" }
            val body = text.trim()
            require(body.isNotEmpty()) { "SMS text must not be empty" }
            val manager = smsManagerFor(subscriptionId) ?: error("SMS is not available on this device")
            val parts = runCatching { manager.divideMessage(body) }.getOrNull()
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: splitSmsText(body)
            if (parts.size <= 1) {
                manager.sendTextMessage(target, null, body, null, null)
            } else {
                manager.sendMultipartTextMessage(target, null, ArrayList(parts), null, null)
            }
            true
        }

    override suspend fun markRead(threadId: String): Boolean = withContext(ioDispatcher) {
        requireRead()
        val values = android.content.ContentValues().apply {
            put(Telephony.Sms.READ, 1)
        }
        resolver.update(
            Telephony.Sms.CONTENT_URI,
            values,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId),
        ) > 0
    }

    override suspend fun delete(threadId: String): Boolean = withContext(ioDispatcher) {
        requireRead()
        resolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId),
        ) > 0
    }

    private fun queryRows(threadId: String?, address: String?, limit: Int): List<SmsRow> {
        val selection = buildList {
            if (!threadId.isNullOrBlank()) add("${Telephony.Sms.THREAD_ID} = ?")
            if (!address.isNullOrBlank()) add("${Telephony.Sms.ADDRESS} = ?")
        }.joinToString(" AND ").takeIf { it.isNotEmpty() }
        val args = buildList {
            if (!threadId.isNullOrBlank()) add(threadId)
            if (!address.isNullOrBlank()) add(address)
        }.toTypedArray()
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC" +
            if (limit > 0) " LIMIT ${limit.coerceAtMost(MAX_ROWS)}" else ""
        val rows = mutableListOf<SmsRow>()
        resolver.query(Telephony.Sms.CONTENT_URI, projection, selection, args, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                rows += SmsRow(
                    threadId = cursor.getLong(0).toString(),
                    address = cursor.getString(1).orEmpty(),
                    body = cursor.getString(2).orEmpty(),
                    dateMs = cursor.getLong(3),
                    incoming = cursor.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    read = cursor.getInt(5) != 0,
                )
            }
        }
        return rows
    }

    private fun smsManagerFor(subscriptionId: Int?): SmsManager? {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return null
        return if (subscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { manager.createForSubscriptionId(subscriptionId) }.getOrDefault(manager)
        } else {
            manager
        }
    }

    private fun requireRead() {
        if (!canRead) {
            throw SecurityException(
                "SMS reading requires READ_SMS permission; grant SMS access for DeepDeep and retry",
            )
        }
    }

    private companion object {
        const val THREAD_SCAN_LIMIT = 500

        const val MAX_ROWS = 5_000

    }
}

internal const val SMS_SINGLE_LIMIT = 160

internal const val SMS_SNIPPET_LENGTH = 120

internal const val SMS_PART_LIMIT = 152
