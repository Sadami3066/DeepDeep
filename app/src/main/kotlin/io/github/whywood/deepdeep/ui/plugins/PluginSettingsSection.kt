package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdDropdown
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.api.SettingSpec

@Composable
internal fun PluginSettingsSection(
    settings: List<SettingSpec>,
    values: Map<String, String>,
    enabled: Boolean,
    resolver: PluginTextResolver,
    onSetting: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (settings.isEmpty()) return
    Column(modifier.padding(horizontal = 24.dp)) {
        Text(
            text = stringResource(R.string.plugins_setting_section),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = DdType.bodySmall,
            color = deepDeepColors().inkTertiary,
        )
        Spacer(Modifier.height(6.dp))
        settings.forEach { top ->
            when (top) {
                is SettingSpec.Group -> {
                    Spacer(Modifier.height(12.dp))
                    DdSectionLabel(resolver.resolve(top.title), Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    PluginGroup {
                        top.items.forEachIndexed { index, spec ->
                            if (index > 0) DdHairline()
                            PluginSettingRow(spec, values, enabled, resolver, onSetting)
                        }
                    }
                }

                else -> PluginGroup {
                    PluginSettingRow(top, values, enabled, resolver, onSetting)
                }
            }
        }
    }
}

@Composable
private fun PluginSettingRow(
    spec: SettingSpec,
    values: Map<String, String>,
    enabled: Boolean,
    resolver: PluginTextResolver,
    onSetting: (String, String) -> Unit,
) {
    if (spec is SettingSpec.Group) return
    val title = resolver.resolve(spec.title)
    val specSummary = spec.summary
    val summary = when {
        specSummary != null -> resolver.resolve(specSummary)
        spec.requiresRestart -> stringResource(R.string.plugins_settings_restart)
        else -> null
    }
    when (spec) {
        is SettingSpec.Toggle -> {
            val checked = values[spec.key]?.toBooleanStrictOrNull() ?: spec.defaultValue
            DdListRow(
                title = title,
                subtitle = summary,
                trailing = {
                    DdSwitch(
                        checked = checked,
                        onCheckedChange = { onSetting(spec.key, it.toString()) },
                        enabled = enabled,
                        modifier = Modifier.testTag("plugin_setting_${spec.key}"),
                    )
                },
            )
        }

        is SettingSpec.Choice -> PluginChoiceSettingRow(spec, title, summary, values, enabled, resolver, onSetting)
        is SettingSpec.Number -> PluginNumberSettingRow(spec, title, summary, values, enabled, onSetting)
        is SettingSpec.Text -> PluginTextSettingRow(spec, title, summary, values, enabled, onSetting)
        is SettingSpec.Group -> Unit
    }
}

@Composable
private fun PluginChoiceSettingRow(
    spec: SettingSpec.Choice,
    title: String,
    summary: String?,
    values: Map<String, String>,
    enabled: Boolean,
    resolver: PluginTextResolver,
    onSetting: (String, String) -> Unit,
) {
    val colors = deepDeepColors()
    var expanded by remember(spec.key) { mutableStateOf(false) }
    val stored = values[spec.key]
    val current = spec.options.firstOrNull { it.value == stored }?.value ?: spec.default
    val currentLabel = spec.options.firstOrNull { it.value == current }
        ?.let { resolver.resolve(it.label) }
        ?: current
    DdDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        anchor = {
            DdListRow(
                title = title,
                subtitle = summary,
                value = currentLabel,
                showChevron = true,
                enabled = enabled,
                onClick = if (enabled) {
                    { expanded = true }
                } else {
                    null
                },
                modifier = Modifier.testTag("plugin_setting_${spec.key}"),
            )
        },
    ) {
        spec.options.forEach { option ->
            DdMenuItem(
                text = resolver.resolve(option.label),
                onClick = {
                    expanded = false
                    onSetting(spec.key, option.value)
                },
                selected = option.value == current,
                trailing = if (option.value == current) {
                    {
                        DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun PluginNumberSettingRow(
    spec: SettingSpec.Number,
    title: String,
    summary: String?,
    values: Map<String, String>,
    enabled: Boolean,
    onSetting: (String, String) -> Unit,
) {
    val colors = deepDeepColors()
    val current = (values[spec.key]?.toIntOrNull() ?: spec.defaultValue).coerceIn(spec.min, spec.max)
    DdListRow(
        title = title,
        subtitle = summary,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DdTextAction(
                    text = "−",
                    onClick = { onSetting(spec.key, (current - 1).toString()) },
                    enabled = enabled && current > spec.min,
                    color = colors.inkSecondary,
                    modifier = Modifier.testTag("plugin_setting_${spec.key}_dec"),
                )
                Text(
                    text = current.toString(),
                    modifier = Modifier.width(36.dp).testTag("plugin_setting_${spec.key}_value"),
                    style = DdType.body,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                DdTextAction(
                    text = "+",
                    onClick = { onSetting(spec.key, (current + 1).toString()) },
                    enabled = enabled && current < spec.max,
                    color = colors.inkSecondary,
                    modifier = Modifier.testTag("plugin_setting_${spec.key}_inc"),
                )
            }
        },
    )
}

@Composable
private fun PluginTextSettingRow(
    spec: SettingSpec.Text,
    title: String,
    summary: String?,
    values: Map<String, String>,
    enabled: Boolean,
    onSetting: (String, String) -> Unit,
) {
    val colors = deepDeepColors()
    var editing by remember(spec.key) { mutableStateOf(false) }
    var draft by remember(spec.key) { mutableStateOf("") }
    val stored = values[spec.key] ?: spec.default
    val display = when {
        stored.isBlank() -> stringResource(R.string.plugins_setting_unset)
        spec.secret -> stringResource(R.string.plugins_setting_secret_set)
        else -> stored
    }
    DdListRow(
        title = title,
        subtitle = summary,
        value = display,
        showChevron = true,
        enabled = enabled,
        onClick = if (enabled) {
            {
                draft = stored
                editing = true
            }
        } else {
            null
        },
        modifier = Modifier.testTag("plugin_setting_${spec.key}"),
    )
    if (editing) {
        val save = {
            editing = false
            onSetting(spec.key, draft)
        }
        DdDialog(
            onDismissRequest = { editing = false },
            title = title,
            content = {
                DdTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "",
                    visualTransformation = if (spec.secret) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = if (spec.secret) {
                        KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                    } else {
                        KeyboardOptions.Default
                    },
                    onDone = save,
                    testTag = "plugin_setting_${spec.key}_input",
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.plugins_cancel),
                    onClick = { editing = false },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.plugins_setting_save),
                    onClick = save,
                    color = colors.accent,
                )
            },
        )
    }
}
