package io.github.whywood.deepdeep.plugin.api

public interface PublisherInfo {
    public val name: String
    public val keyId: String
}

public interface UiManifest {
    public val apiLevel: Int
    public val kotlin: String?
    public val compose: String?
}

public interface Provides {
    public val tools: List<String>
    public val skills: List<String>
    public val uiSlots: List<String>
    public val pages: List<String>
    public val themes: List<String>
        get() = emptyList()

    public val isEmpty: Boolean
        get() = tools.isEmpty() && skills.isEmpty() && uiSlots.isEmpty() && pages.isEmpty() && themes.isEmpty()
}

public interface PluginComponentDeclaration {
    public val background: Boolean

    public val foregroundService: Boolean

    public val notificationListener: Boolean

    public val accessibility: Boolean

    public val share: Boolean

    public val deeplinks: List<String>
}

public interface PluginManifest {
    public val id: String
    public val version: String
    public val apiLevel: Int
    public val minHostVersion: String?
    public val ui: UiManifest?
    public val name: LocalizedText
    public val summary: LocalizedText
    public val description: LocalizedText?
    public val homepage: String?
    public val changelog: String?
    public val icon: IconRef?
    public val entryClass: String?
    public val publisher: PublisherInfo?
    public val provides: Provides
    public val capabilities: Set<PluginCapability>
    public val defaultEnabled: Boolean
    public val permissions: List<String>
        get() = emptyList()
    public val components: PluginComponentDeclaration?
        get() = null
}
