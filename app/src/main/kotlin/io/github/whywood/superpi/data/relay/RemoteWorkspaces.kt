package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.runtime.RemoteBotRef
import io.github.whywood.superpi.runtime.RuntimeWorkspace
import java.io.File

data class RemoteBot(
    val deviceId: String,
    val deviceName: String,
    val botId: String,
    val botName: String,
    val online: Boolean,
)

object RemoteWorkspaces {
    private const val ID_PREFIX = "remote:"

    fun workspaceId(bot: RemoteBot): String = "$ID_PREFIX${bot.deviceId}:${bot.botId}"

    fun directory(root: File, bot: RemoteBot): File =
        File(File(root, safeSegment(bot.deviceId)), safeSegment(bot.botId))

    fun workspace(root: File, bot: RemoteBot): RuntimeWorkspace = RuntimeWorkspace(
        id = workspaceId(bot),
        name = bot.botName,
        cwd = directory(root, bot).absolutePath,
        remote = RemoteBotRef(
            deviceId = bot.deviceId,
            deviceName = bot.deviceName,
            botId = bot.botId,
            botName = bot.botName,
            online = bot.online,
        ),
    )

    fun workspaces(root: File, bots: List<RemoteBot>): List<RuntimeWorkspace> =
        bots.map { bot -> workspace(root, bot) }

    fun remoteRoot(filesDir: File): File = File(filesDir, "remote")

    private fun safeSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
}
