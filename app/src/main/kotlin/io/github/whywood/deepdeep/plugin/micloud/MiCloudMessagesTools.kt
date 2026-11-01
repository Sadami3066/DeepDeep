package io.github.whywood.deepdeep.plugin.micloud

import dev.pi.coding.tools.CodingTool
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun smsTool(client: MiCloudReadApi): CodingTool = CodingTool(
    name = "mi_cloud_sms",
    description = "List recent SMS threads (optionally with call records) from Xiaomi Cloud. Read-only.",
    parameters = smsParameters,
    label = "mi_cloud_sms",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miCloudToolResult {
            val userId = requiredUserId(client)
            val limit = limitArg(args)
            val response = client.getJson(
                smsThreadPath(
                    userId = userId,
                    ts = now(),
                    limit = limit,
                    withPhoneCall = args.argBool("withPhoneCall") ?: true,
                    syncTag = args.argString("syncTag") ?: "0",
                    syncThreadTag = args.argString("syncThreadTag") ?: "0",
                ),
            )
            val payload = response.payloadObject()
            val entries = response.payloadArray("entries")
                .mapNotNull { it as? JsonObject }
                .map(::compactObject)
            val watermark = payload?.get("watermark")
            val extra = buildJsonObject {
                if (watermark != null && watermark !is JsonNull) put("next", watermark)
            }
            itemsResult(entries, extra)
        }
    },
)

internal fun smsThreadPath(
    userId: String,
    ts: Long,
    limit: Int,
    withPhoneCall: Boolean,
    syncTag: String,
    syncThreadTag: String,
): String = "/sms/${encodeComponent(userId)}/full/thread" +
    "?limit=$limit&readMode=older&withPhoneCall=$withPhoneCall&_dc=$ts" +
    "&syncTag=${encodeComponent(syncTag)}&syncThreadTag=${encodeComponent(syncThreadTag)}"

private val smsParameters: JsonObject = toolParams {
    put("limit", propInt("Number of threads to return", minimum = 1, maximum = MAX_LIST_LIMIT, default = DEFAULT_LIST_LIMIT))
    put("withPhoneCall", propBool("Include call records in the response (default true)", default = true))
    put("syncTag", propString("Sync tag returned by a previous call (default 0)"))
    put("syncThreadTag", propString("Thread sync tag returned by a previous call (default 0)"))
}
