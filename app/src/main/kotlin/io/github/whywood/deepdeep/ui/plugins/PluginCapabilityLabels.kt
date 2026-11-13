package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.PluginCapability

@Composable
internal fun capabilitySummary(capabilities: Set<String>): String {
    if (capabilities.isEmpty()) return ""
    return buildString {
        capabilities.sorted().forEachIndexed { index, wire ->
            if (index > 0) append(", ")
            val capability = PluginCapability.fromWire(wire)
            append(if (capability == null) wire else stringResource(capabilityLabelRes(capability)))
        }
    }
}

private fun capabilityLabelRes(capability: PluginCapability): Int = when (capability) {
    PluginCapability.LINUX -> R.string.plugins_capability_linux
    PluginCapability.SCREEN -> R.string.plugins_capability_screen
    PluginCapability.NOTIFICATIONS -> R.string.plugins_capability_notifications
    PluginCapability.SECRETS -> R.string.plugins_capability_secrets
    PluginCapability.NETWORK -> R.string.plugins_capability_network
    PluginCapability.FILES -> R.string.plugins_capability_files
    PluginCapability.SESSIONS -> R.string.plugins_capability_sessions
    PluginCapability.JOBS -> R.string.plugins_capability_jobs
    PluginCapability.TIMERS -> R.string.plugins_capability_timers
    PluginCapability.GOALS -> R.string.plugins_capability_goals
    PluginCapability.MINIAPP -> R.string.plugins_capability_miniapp
    PluginCapability.MCP -> R.string.plugins_capability_mcp
    PluginCapability.UI -> R.string.plugins_capability_ui
    PluginCapability.APP -> R.string.plugins_capability_app
    PluginCapability.BUS -> R.string.plugins_capability_bus
    PluginCapability.LOGS -> R.string.plugins_capability_logs
    PluginCapability.CHAT -> R.string.plugins_capability_chat
    PluginCapability.PROMPT -> R.string.plugins_capability_prompt
    PluginCapability.SETTINGS -> R.string.plugins_capability_settings
    PluginCapability.DEVICE -> R.string.plugins_capability_device
    PluginCapability.CLIPBOARD -> R.string.plugins_capability_clipboard
    PluginCapability.PERMISSIONS -> R.string.plugins_capability_permissions
    PluginCapability.PACKAGES -> R.string.plugins_capability_packages
    PluginCapability.PRIVILEGE -> R.string.plugins_capability_privilege
    PluginCapability.INTENTS -> R.string.plugins_capability_intents
    PluginCapability.COMPONENTS -> R.string.plugins_capability_components
    PluginCapability.BACKGROUND -> R.string.plugins_capability_background
    PluginCapability.LOCATION -> R.string.plugins_capability_location
    PluginCapability.CONTACTS -> R.string.plugins_capability_contacts
    PluginCapability.SMS -> R.string.plugins_capability_sms
    PluginCapability.CALENDAR -> R.string.plugins_capability_calendar
    PluginCapability.MEDIA -> R.string.plugins_capability_media
    PluginCapability.SPEECH -> R.string.plugins_capability_speech
    PluginCapability.WEB -> R.string.plugins_capability_web
    PluginCapability.CREDENTIALS -> R.string.plugins_capability_credentials
    PluginCapability.ACCESSIBILITY -> R.string.plugins_capability_accessibility
    PluginCapability.SHARE -> R.string.plugins_capability_share
    PluginCapability.DEEPLINK -> R.string.plugins_capability_deeplink
    else -> R.string.plugins_capability_other
}
