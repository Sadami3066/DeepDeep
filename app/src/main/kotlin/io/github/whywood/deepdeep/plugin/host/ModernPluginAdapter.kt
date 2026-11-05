package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.OfficialPlugin
import io.github.whywood.deepdeep.plugin.PluginAction
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginDetailRow
import io.github.whywood.deepdeep.plugin.PluginKind
import io.github.whywood.deepdeep.plugin.PluginProgress
import io.github.whywood.deepdeep.plugin.PluginSettingOption
import io.github.whywood.deepdeep.plugin.PluginSettingSpec
import io.github.whywood.deepdeep.plugin.PluginSkill
import io.github.whywood.deepdeep.plugin.PluginSkillFile
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.PluginUi
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.plugin.api.PluginAvailability as ModernAvailability
import io.github.whywood.deepdeep.plugin.api.PluginSetupUi as ModernSetupUi
import io.github.whywood.deepdeep.plugin.api.PluginSkill as ModernSkill
import io.github.whywood.deepdeep.plugin.api.PluginStatus as ModernStatus
import io.github.whywood.deepdeep.runtime.BoundSession
import io.github.whywood.deepdeep.runtime.SessionBinding

internal class ModernPluginAdapter(
    val modern: DeepDeepPlugin,
    val manifest: PluginManifest,
    private val host: PluginHost,
) : OfficialPlugin {
    override val id: String get() = manifest.id

    override val kind: PluginKind
        get() = if (manifest.source == PluginSource.BUILTIN) PluginKind.INTERNAL else PluginKind.EXTERNAL

    override val requiresPrivilege: Boolean get() = manifest.requiresPrivilege

    override val defaultEnabled: Boolean get() = manifest.defaultEnabled

    override val icon: DdIcon
        get() = (manifest.icon as? IconRef.Resource)?.let { DdIcon(it.id) } ?: DdIcons.Puzzle

    override val nameRes: Int get() = resource(manifest.name, R.string.plugins_size_builtin)

    override val summaryRes: Int get() = resource(manifest.summary, R.string.plugins_size_builtin)

    override val version: String get() = manifest.version

    override val sizeLabelRes: Int
        get() = (manifest.sizeLabel as? LocalizedText.Resource)?.id ?: R.string.plugins_size_builtin

    override suspend fun availability(): PluginAvailability = when (val availability = modern.availability(host)) {
        is ModernAvailability.Available -> PluginAvailability.Available
        is ModernAvailability.Unavailable -> PluginAvailability.Unavailable(availability.reason)
    }

    override suspend fun install(progress: PluginProgress) {
        modern.install(
            host,
            object : io.github.whywood.deepdeep.plugin.api.PluginProgress {
                override fun report(percent: Int?, label: LocalizedText) {
                    progress.report(percent, resource(label, R.string.plugins_size_builtin))
                }
            },
        )
    }

    override suspend fun uninstall() = modern.uninstall(host)

    override suspend fun recover() = Unit

    override suspend fun status(): PluginStatus = modern.status(host).toLegacy()

    override suspend fun setup(ui: PluginUi) = modern.setup(host, SetupUiAdapter(ui))

    override fun actions(status: PluginStatus): List<PluginAction> =
        modern.actions(host, status.toModern()).map { action ->
            PluginAction(
                id = action.id,
                labelRes = resource(action.label, R.string.plugins_size_builtin),
                style = when (action.style) {
                    io.github.whywood.deepdeep.plugin.api.PluginActionStyle.NORMAL ->
                        io.github.whywood.deepdeep.plugin.PluginActionStyle.NORMAL

                    io.github.whywood.deepdeep.plugin.api.PluginActionStyle.DESTRUCTIVE ->
                        io.github.whywood.deepdeep.plugin.PluginActionStyle.DESTRUCTIVE
                },
            )
        }

    override suspend fun runAction(actionId: String, ui: PluginUi) =
        modern.runAction(host, actionId, SetupUiAdapter(ui))

    override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> =
        modern.detailRows(host, status.toModern()).map { row ->
            PluginDetailRow(
                labelRes = resource(row.label, R.string.plugins_size_builtin),
                value = row.value,
            )
        }

    override fun skills(): List<PluginSkill> = modern.skills(host).map { it.toLegacy() }

    override fun promptBody(status: PluginStatus): String? =
        modern.promptSections(host)
            .sortedBy { it.priority }
            .joinToString("\n\n") { it.body }
            .takeIf { it.isNotBlank() }

    override fun tools(binding: SessionBinding): List<dev.pi.coding.tools.CodingTool> {
        val session = binding.current()?.toSessionInfo(binding.supportsChatDelivery)
            ?: PluginSessionInfo("", "", null, binding.supportsChatDelivery)
        val contributions = modern.contributionsFor(host, session)
        return PluginToolAdapter.toCodingTools(contributions.tools)
    }

    override fun tools(): List<dev.pi.coding.tools.CodingTool> = tools(SessionBinding())

    override fun settings(): List<PluginSettingSpec> = modern.settings()
        .flatMap { spec -> if (spec is SettingSpec.Group) spec.items else listOf(spec) }
        .mapNotNull(::toLegacySetting)

    override suspend fun onSettingsChanged(values: Map<String, String>) =
        modern.onSettingsChanged(host, values)

    private fun BoundSession.toSessionInfo(supportsChatDelivery: Boolean): PluginSessionInfo =
        PluginSessionInfo(
            sessionId = sessionId,
            cwd = cwd,
            file = sessionFile,
            supportsChatDelivery = supportsChatDelivery,
        )

    private fun toLegacySetting(spec: SettingSpec): PluginSettingSpec? = when (spec) {
        is SettingSpec.Toggle -> PluginSettingSpec.Toggle(
            key = spec.key,
            titleRes = resource(spec.title, R.string.plugins_size_builtin),
            defaultValue = spec.defaultValue,
        )

        is SettingSpec.Choice -> PluginSettingSpec.Choice(
            key = spec.key,
            titleRes = resource(spec.title, R.string.plugins_size_builtin),
            options = spec.options.map { option ->
                PluginSettingOption(option.value, resource(option.label, R.string.plugins_size_builtin))
            },
            default = spec.default,
        )

        is SettingSpec.Number -> PluginSettingSpec.Number(
            key = spec.key,
            titleRes = resource(spec.title, R.string.plugins_size_builtin),
            min = spec.min,
            max = spec.max,
            defaultValue = spec.defaultValue,
        )

        is SettingSpec.Text -> PluginSettingSpec.Text(
            key = spec.key,
            titleRes = resource(spec.title, R.string.plugins_size_builtin),
            default = spec.default,
            secret = spec.secret,
        )

        is SettingSpec.Group -> null
    }

    private fun PluginStatus.toModern(): ModernStatus = when (this) {
        is PluginStatus.Ready -> ModernStatus.Ready(label, account)
        is PluginStatus.NotReady -> ModernStatus.NotReady(label)
        is PluginStatus.Failed -> ModernStatus.Failed(label)
    }

    private fun ModernStatus.toLegacy(): PluginStatus = when (this) {
        is ModernStatus.Ready -> PluginStatus.Ready(label, account)
        is ModernStatus.NotReady -> PluginStatus.NotReady(label)
        is ModernStatus.Failed -> PluginStatus.Failed(label)
    }

    private fun ModernSkill.toLegacy(): PluginSkill = PluginSkill(
        id = id,
        markdown = markdown,
        files = files.map { PluginSkillFile(path = it.path, content = it.content.orEmpty()) },
    )

    private fun resource(text: LocalizedText, fallback: Int): Int =
        (text as? LocalizedText.Resource)?.id ?: fallback

    private inner class SetupUiAdapter(private val ui: PluginUi) : ModernSetupUi {
        override fun stage(
            title: LocalizedText,
            message: LocalizedText?,
            url: String?,
            imagePath: String?,
            progress: Int?,
        ) {
            ui.stage(
                titleRes = resource(title, R.string.plugins_size_builtin),
                messageRes = message?.let { resource(it, R.string.plugins_size_builtin) },
                url = url,
                progress = progress,
                imagePath = imagePath,
            )
        }

        override fun progress(percent: Int?, label: LocalizedText) {
            ui.progress(percent, resource(label, R.string.plugins_size_builtin))
        }

        override fun cancelled(): Boolean = ui.cancelled()
    }
}
