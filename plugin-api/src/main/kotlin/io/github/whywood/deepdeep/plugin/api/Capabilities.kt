package io.github.whywood.deepdeep.plugin.api

@JvmInline
public value class PluginCapability private constructor(public val wire: String) {
    public companion object {
        public val LINUX: PluginCapability = PluginCapability("linux")
        public val SCREEN: PluginCapability = PluginCapability("screen")
        public val NOTIFICATIONS: PluginCapability = PluginCapability("notifications")
        public val SECRETS: PluginCapability = PluginCapability("secrets")
        public val NETWORK: PluginCapability = PluginCapability("network")
        public val FILES: PluginCapability = PluginCapability("files")
        public val SESSIONS: PluginCapability = PluginCapability("sessions")
        public val JOBS: PluginCapability = PluginCapability("jobs")
        public val TIMERS: PluginCapability = PluginCapability("timers")
        public val GOALS: PluginCapability = PluginCapability("goals")
        public val MINIAPP: PluginCapability = PluginCapability("miniapp")
        public val MCP: PluginCapability = PluginCapability("mcp")
        public val UI: PluginCapability = PluginCapability("ui")
        public val APP: PluginCapability = PluginCapability("app")
        public val BUS: PluginCapability = PluginCapability("bus")
        public val LOGS: PluginCapability = PluginCapability("logs")
        public val CHAT: PluginCapability = PluginCapability("chat")
        public val PROMPT: PluginCapability = PluginCapability("prompt")

        public val ALL: List<PluginCapability> = listOf(
            LINUX,
            SCREEN,
            NOTIFICATIONS,
            SECRETS,
            NETWORK,
            FILES,
            SESSIONS,
            JOBS,
            TIMERS,
            GOALS,
            MINIAPP,
            MCP,
            UI,
            APP,
            BUS,
            LOGS,
            CHAT,
            PROMPT,
        )

        public fun fromWire(value: String): PluginCapability? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}
