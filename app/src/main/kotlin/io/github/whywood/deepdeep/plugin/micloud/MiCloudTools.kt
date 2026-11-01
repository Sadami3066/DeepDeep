package io.github.whywood.deepdeep.plugin.micloud

import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.io.File
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

private class ClientReadApi(private val client: MiCloudClient) : MiCloudReadApi {
    override suspend fun userId(): String? = client.userId()
    override suspend fun getJson(pathWithQuery: String): JsonElement = client.getJson(pathWithQuery)
    override suspend fun download(pathWithQuery: String, dest: File): File = client.download(pathWithQuery, dest)
}

private class WriterWriteApi(private val writer: MiCloudWebWriter) : MiCloudWriteApi {
    override suspend fun post(path: String, form: Map<String, String>): String = writer.post(path, form)
}

fun miCloudTools(
    client: MiCloudClient,
    settings: MiCloudSettings,
    writer: MiCloudWebWriter,
    downloadDir: File,
    downloadLinuxDir: String,
): List<CodingTool> = buildMiCloudTools(
    client = ClientReadApi(client),
    settings = settings,
    writer = WriterWriteApi(writer),
    downloadDir = downloadDir,
    downloadLinuxDir = downloadLinuxDir,
)

internal fun buildMiCloudTools(
    client: MiCloudReadApi,
    settings: MiCloudSettings,
    writer: MiCloudWriteApi,
    downloadDir: File,
    downloadLinuxDir: String,
): List<CodingTool> {
    val capabilities = settings.capabilities.value
    return buildList {
        if (capabilities.accountRead) add(accountTool(client))
        if (capabilities.notesRead) add(notesTool(client, settings, writer))
        if (capabilities.todosRead) add(todoTool(client, settings, writer))
        if (capabilities.smsRead) add(smsTool(client))
        if (capabilities.contactsRead) add(contactsTool(client))
        if (capabilities.galleryRead) add(galleryTool(client))
        if (capabilities.recordingsRead) add(recordingsTool(client, downloadDir, downloadLinuxDir))
    }
}

private fun accountTool(client: MiCloudReadApi): CodingTool = CodingTool(
    name = "mi_cloud_account",
    description = "Read the signed-in Xiaomi Cloud account profile or storage quota. Read-only.",
    parameters = accountParameters,
    label = "mi_cloud_account",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miCloudToolResult {
            val action = args.argString("action") ?: "profile"
            val target = when (action) {
                "profile" -> "/status/lite/profile?ts=${now()}"
                "quota" -> "/status/vip/quota/summary?ts=${now()}"
                else -> throw IllegalArgumentException("action 无效：$action")
            }
            textResult(compactPayload(client.getJson(target)))
        }
    },
)

private val accountParameters: JsonObject = toolParams {
    put(
        "action",
        propEnum("What to read: profile or quota (default profile)", listOf("profile", "quota")),
    )
}
