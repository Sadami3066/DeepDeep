package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.plugin.arrange.ArrangePlugin
import io.github.whywood.deepdeep.plugin.gui.GuiPlugin
import io.github.whywood.deepdeep.plugin.micloud.MiCloudPlugin

internal fun androidGui(wiring: BuiltinPluginWiring): OfficialPlugin = GuiPlugin(
    context = wiring.uiContext,
    screenshotDir = wiring.screenshotDir,
    mainShell = wiring.mainShell,
    privilege = wiring.privilege,
    virtualScreen = wiring.virtualScreen,
)

internal fun androidArrange(wiring: BuiltinPluginWiring): OfficialPlugin = ArrangePlugin(
    context = wiring.uiContext,
)

internal fun androidMiCloud(wiring: BuiltinPluginWiring): OfficialPlugin = MiCloudPlugin(
    context = wiring.uiContext,
    onCapabilitiesChanged = { wiring.onCapabilitiesChanged() },
    onSessionInvalidated = { wiring.onSessionInvalidated() },
)
