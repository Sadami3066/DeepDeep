package io.github.whywood.deepdeep.plugin

import androidx.annotation.StringRes
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.runtime.SessionBinding

enum class PluginKind {
    EXTERNAL,
    INTERNAL,
}

interface OfficialPlugin {
    val id: String

    val kind: PluginKind get() = PluginKind.EXTERNAL

    val icon: DdIcon

    val tintIcon: Boolean get() = true

    @get:StringRes
    val nameRes: Int

    @get:StringRes
    val summaryRes: Int

    val version: String

    @get:StringRes
    val sizeLabelRes: Int

    suspend fun availability(): PluginAvailability

    suspend fun install(progress: PluginProgress)

    suspend fun uninstall()

    suspend fun recover() {}

    suspend fun status(): PluginStatus

    suspend fun setup(ui: PluginUi) {}

    fun actions(status: PluginStatus): List<PluginAction> = emptyList()

    suspend fun runAction(actionId: String, ui: PluginUi) {}

    suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = emptyList()

    fun skills(): List<PluginSkill> = emptyList()

    fun tools(): List<CodingTool> = emptyList()

    fun tools(binding: SessionBinding): List<CodingTool> = tools()

    fun settings(): List<PluginSettingSpec> = emptyList()

    suspend fun onSettingsChanged(values: Map<String, String>) {}

    fun promptBody(status: PluginStatus): String? = null
}

sealed interface PluginAvailability {
    data object Available : PluginAvailability

    data class Unavailable(val reason: String) : PluginAvailability
}

sealed interface PluginStatus {
    val label: String

    data class Ready(
        override val label: String,
        val account: String? = null,
    ) : PluginStatus

    data class NotReady(override val label: String) : PluginStatus

    data class Failed(override val label: String) : PluginStatus
}

interface PluginProgress {
    fun report(percent: Int?, @StringRes labelRes: Int)
}

interface PluginUi {
    fun stage(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int? = null,
        url: String? = null,
        progress: Int? = null,
        imagePath: String? = null,
    )

    fun custom(content: PluginSetupContent)

    fun progress(percent: Int?, @StringRes labelRes: Int)

    fun cancelled(): Boolean
}

enum class PluginActionStyle {
    NORMAL,
    DESTRUCTIVE,
}

data class PluginAction(
    val id: String,
    @StringRes val labelRes: Int,
    val style: PluginActionStyle = PluginActionStyle.NORMAL,
)

data class PluginDetailRow(
    @StringRes val labelRes: Int,
    val value: String,
)

data class PluginSkillFile(
    val path: String,
    val content: String,
)

data class PluginSkill(
    val id: String,
    val markdown: String,
    val files: List<PluginSkillFile> = emptyList(),
    val legacyOwned: Boolean = false,
)

interface PluginStrings {
    fun get(@StringRes id: Int): String

    fun format(@StringRes id: Int, vararg args: Any): String
}
