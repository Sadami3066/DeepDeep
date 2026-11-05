package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.feishu.FEISHU_SKILL_ID
import io.github.whywood.deepdeep.plugin.feishu.FEISHU_VERSION
import io.github.whywood.deepdeep.plugin.feishu.FeishuPlugin
import io.github.whywood.deepdeep.plugin.host.builtinPluginManifest
import io.github.whywood.deepdeep.plugin.jwxt.JWXT_SETTING_NETID
import io.github.whywood.deepdeep.plugin.jwxt.JWXT_SETTING_PASSWORD
import io.github.whywood.deepdeep.plugin.jwxt.JwxtCli
import io.github.whywood.deepdeep.plugin.jwxt.JwxtPlugin
import io.github.whywood.deepdeep.plugin.wecom.WECOM_SKILL_ID
import io.github.whywood.deepdeep.plugin.wecom.WecomCli
import io.github.whywood.deepdeep.plugin.wecom.WecomPlugin

internal fun cliFeishu(wiring: BuiltinPluginWiring): OfficialPlugin = wiring.modern(
    FeishuPlugin(
        context = wiring.uiContext,
        linux = wiring.linux,
        linuxSettings = wiring.linuxSettings,
        downloader = PluginDownloader(),
        cacheDir = wiring.cacheDir,
    ),
    cliFeishuManifest(),
)

internal fun cliFeishuManifest(): PluginManifest = builtinPluginManifest(
    id = "feishu",
    version = FEISHU_VERSION,
    nameRes = R.string.plugin_feishu_name,
    summaryRes = R.string.plugin_feishu_summary,
    iconRes = R.drawable.ic_plugin_feishu,
    sizeLabelRes = R.string.plugin_feishu_size,
    skills = listOf(FEISHU_SKILL_ID),
    source = PluginSource.EXTERNAL,
    tintIcon = false,
)

internal fun cliWecom(wiring: BuiltinPluginWiring): OfficialPlugin = wiring.modern(
    WecomPlugin(
        context = wiring.uiContext,
        linux = wiring.linux,
        linuxSettings = wiring.linuxSettings,
        downloader = PluginDownloader(),
        workspaceDir = wiring.workspaceDir,
    ),
    cliWecomManifest(),
)

internal fun cliWecomManifest(): PluginManifest = builtinPluginManifest(
    id = "wecom",
    version = WecomCli.VERSION,
    nameRes = R.string.plugin_wecom_name,
    summaryRes = R.string.plugin_wecom_summary,
    iconRes = DdIcons.Message.res,
    sizeLabelRes = R.string.plugin_wecom_size,
    skills = listOf(WECOM_SKILL_ID),
    source = PluginSource.EXTERNAL,
)

internal fun cliJwxt(wiring: BuiltinPluginWiring): OfficialPlugin = wiring.modern(
    JwxtPlugin(
        context = wiring.uiContext,
        linux = wiring.linux,
        linuxSettings = wiring.linuxSettings,
    ),
    cliJwxtManifest(),
)

internal fun cliJwxtManifest(): PluginManifest = builtinPluginManifest(
    id = "jwxt",
    version = JwxtCli.VERSION,
    nameRes = R.string.plugin_jwxt_name,
    summaryRes = R.string.plugin_jwxt_summary,
    iconRes = DdIcons.Globe.res,
    sizeLabelRes = R.string.plugin_jwxt_size,
    skills = listOf(JwxtCli.SKILL_ID),
    settings = listOf(JWXT_SETTING_NETID, JWXT_SETTING_PASSWORD),
    source = PluginSource.EXTERNAL,
)
