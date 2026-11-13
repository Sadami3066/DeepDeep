package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

public data class PluginPickedFile(
    public val path: String,
    public val uri: String?,
    public val name: String,
    public val sizeBytes: Long,
    public val mimeType: String?,
)

public interface UiConfirmRequest {
    public val title: String
    public val message: String?
    public val confirmLabel: String?
    public val cancelLabel: String?
    public val destructive: Boolean
}

public fun uiConfirmRequest(
    title: String,
    message: String? = null,
    confirmLabel: String? = null,
    cancelLabel: String? = null,
    destructive: Boolean = false,
): UiConfirmRequest = UiConfirmRequestImpl(title, message, confirmLabel, cancelLabel, destructive)

internal data class UiConfirmRequestImpl(
    override val title: String,
    override val message: String?,
    override val confirmLabel: String?,
    override val cancelLabel: String?,
    override val destructive: Boolean,
) : UiConfirmRequest

public interface UiPromptRequest {
    public val title: String
    public val message: String?
    public val initial: String
    public val placeholder: String?
    public val multiline: Boolean
}

public fun uiPromptRequest(
    title: String,
    message: String? = null,
    initial: String = "",
    placeholder: String? = null,
    multiline: Boolean = false,
): UiPromptRequest = UiPromptRequestImpl(title, message, initial, placeholder, multiline)

internal data class UiPromptRequestImpl(
    override val title: String,
    override val message: String?,
    override val initial: String,
    override val placeholder: String?,
    override val multiline: Boolean,
) : UiPromptRequest

public interface UiChoice {
    public val id: String
    public val label: String
    public val description: String?
}

public fun uiChoice(id: String, label: String, description: String? = null): UiChoice =
    UiChoiceImpl(id, label, description)

internal data class UiChoiceImpl(
    override val id: String,
    override val label: String,
    override val description: String?,
) : UiChoice

public interface UiChooseRequest {
    public val title: String
    public val message: String?
    public val options: List<UiChoice>
}

public fun uiChooseRequest(
    title: String,
    options: List<UiChoice>,
    message: String? = null,
): UiChooseRequest = UiChooseRequestImpl(title, message, options)

internal data class UiChooseRequestImpl(
    override val title: String,
    override val message: String?,
    override val options: List<UiChoice>,
) : UiChooseRequest

public interface UiDatePickRequest {
    public val initialMs: Long?
    public val minMs: Long?
    public val maxMs: Long?
}

public fun uiDatePickRequest(
    initialMs: Long? = null,
    minMs: Long? = null,
    maxMs: Long? = null,
): UiDatePickRequest = UiDatePickRequestImpl(initialMs, minMs, maxMs)

internal data class UiDatePickRequestImpl(
    override val initialMs: Long?,
    override val minMs: Long?,
    override val maxMs: Long?,
) : UiDatePickRequest

public interface UiTimePickRequest {
    public val initialHour: Int?
    public val initialMinute: Int?
    public val is24Hour: Boolean
}

public fun uiTimePickRequest(
    initialHour: Int? = null,
    initialMinute: Int? = null,
    is24Hour: Boolean = true,
): UiTimePickRequest = UiTimePickRequestImpl(initialHour, initialMinute, is24Hour)

internal data class UiTimePickRequestImpl(
    override val initialHour: Int?,
    override val initialMinute: Int?,
    override val is24Hour: Boolean,
) : UiTimePickRequest

public enum class UiImageSource {
    GALLERY,
    CAMERA,
}

public interface UiFilePickRequest {
    public val mimeTypes: List<String>
    public val multiple: Boolean
    public val maxCount: Int
}

public fun uiFilePickRequest(
    mimeTypes: List<String> = listOf("*/*"),
    multiple: Boolean = true,
    maxCount: Int = 8,
): UiFilePickRequest = UiFilePickRequestImpl(mimeTypes, multiple, maxCount)

internal data class UiFilePickRequestImpl(
    override val mimeTypes: List<String>,
    override val multiple: Boolean,
    override val maxCount: Int,
) : UiFilePickRequest

public interface UiImagePickRequest {
    public val source: UiImageSource
    public val frontCamera: Boolean
}

public fun uiImagePickRequest(
    source: UiImageSource = UiImageSource.GALLERY,
    frontCamera: Boolean = false,
): UiImagePickRequest = UiImagePickRequestImpl(source, frontCamera)

internal data class UiImagePickRequestImpl(
    override val source: UiImageSource,
    override val frontCamera: Boolean,
) : UiImagePickRequest

@JvmInline
public value class PluginPagePresentation private constructor(public val wire: String) {
    public companion object {
        public val PAGE: PluginPagePresentation = PluginPagePresentation("page")
        public val SHEET: PluginPagePresentation = PluginPagePresentation("sheet")
        public val ALL: List<PluginPagePresentation> = listOf(PAGE, SHEET)

        public fun fromWire(value: String): PluginPagePresentation? = ALL.firstOrNull { it.wire == value }
    }

    override fun toString(): String = wire
}

public interface PluginPageContext {
    public val pageId: String
    public val arguments: JsonObject
    public val presentation: PluginPagePresentation
        get() = PluginPagePresentation.PAGE
}
