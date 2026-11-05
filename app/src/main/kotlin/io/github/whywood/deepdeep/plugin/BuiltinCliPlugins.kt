package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.plugin.feishu.FeishuPlugin
import io.github.whywood.deepdeep.plugin.jwxt.JwxtPlugin
import io.github.whywood.deepdeep.plugin.wecom.WecomPlugin

internal fun cliFeishu(wiring: BuiltinPluginWiring): OfficialPlugin = FeishuPlugin(
    context = wiring.uiContext,
    linux = wiring.linux,
    linuxSettings = wiring.linuxSettings,
    downloader = PluginDownloader(),
    cacheDir = wiring.cacheDir,
)

internal fun cliWecom(wiring: BuiltinPluginWiring): OfficialPlugin = WecomPlugin(
    context = wiring.uiContext,
    linux = wiring.linux,
    linuxSettings = wiring.linuxSettings,
    downloader = PluginDownloader(),
    workspaceDir = wiring.workspaceDir,
)

internal fun cliJwxt(wiring: BuiltinPluginWiring): OfficialPlugin = JwxtPlugin(
    context = wiring.uiContext,
    linux = wiring.linux,
    linuxSettings = wiring.linuxSettings,
)
