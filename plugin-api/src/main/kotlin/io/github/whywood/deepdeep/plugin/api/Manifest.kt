package io.github.whywood.deepdeep.plugin.api

data class PublisherInfo(
    val name: String,
    val keyId: String,
)

data class UiManifest(
    val apiLevel: Int = HOST_UI_API_LEVEL,
    val kotlin: String? = null,
    val compose: String? = null,
)

data class Provides(
    val tools: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val settings: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val events: List<String> = emptyList(),
    val uiSlots: List<String> = emptyList(),
    val pages: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tools.isEmpty() &&
            skills.isEmpty() &&
            settings.isEmpty() &&
            actions.isEmpty() &&
            events.isEmpty() &&
            uiSlots.isEmpty() &&
            pages.isEmpty()
}

data class PluginManifest(
    val id: String,
    val version: String,
    val apiLevel: Int = PLUGIN_API_LEVEL,
    val minHostVersion: String? = null,
    val ui: UiManifest? = null,
    val name: LocalizedText,
    val summary: LocalizedText,
    val description: LocalizedText? = null,
    val sizeLabel: LocalizedText? = null,
    val homepage: String? = null,
    val changelog: String? = null,
    val icon: IconRef? = null,
    val source: PluginSource = PluginSource.EXTERNAL,
    val entryClass: String? = null,
    val publisher: PublisherInfo? = null,
    val requires: List<String> = emptyList(),
    val provides: Provides = Provides(),
    val installable: Boolean = true,
    val order: Int = 0,
    val requiresPrivilege: Boolean = false,
    val defaultEnabled: Boolean = true,
)
