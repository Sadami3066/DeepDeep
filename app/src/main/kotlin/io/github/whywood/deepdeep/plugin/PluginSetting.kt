package io.github.whywood.deepdeep.plugin

import androidx.annotation.StringRes

data class PluginSettingOption(
    val value: String,
    @param:StringRes val labelRes: Int,
)

sealed interface PluginSettingSpec {
    val key: String

    @get:StringRes
    val titleRes: Int

    val default: String

    data class Toggle(
        override val key: String,
        @get:StringRes override val titleRes: Int,
        val defaultValue: Boolean = false,
    ) : PluginSettingSpec {
        override val default: String get() = defaultValue.toString()
    }

    data class Choice(
        override val key: String,
        @get:StringRes override val titleRes: Int,
        val options: List<PluginSettingOption>,
        override val default: String,
    ) : PluginSettingSpec {
        init {
            require(options.isNotEmpty()) { "choice setting needs options: $key" }
            require(options.any { it.value == default }) { "default choice is missing: $key" }
        }
    }

    data class Number(
        override val key: String,
        @get:StringRes override val titleRes: Int,
        val min: Int,
        val max: Int,
        val defaultValue: Int,
    ) : PluginSettingSpec {
        init {
            require(min <= max) { "invalid setting range: $key" }
            require(defaultValue in min..max) { "default value is out of range: $key" }
        }

        override val default: String get() = defaultValue.toString()
    }
}

internal fun PluginSettingSpec.normalize(raw: String): String? = when (this) {
    is PluginSettingSpec.Toggle -> when (raw) {
        "true", "false" -> raw
        else -> null
    }

    is PluginSettingSpec.Choice -> raw.takeIf { candidate -> options.any { it.value == candidate } }
    is PluginSettingSpec.Number -> raw.toIntOrNull()?.coerceIn(min, max)?.toString()
}
