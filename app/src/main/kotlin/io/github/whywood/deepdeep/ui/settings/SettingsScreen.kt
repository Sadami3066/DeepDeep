package io.github.whywood.deepdeep.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.DeepSeekModels
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.shell.LinuxInstallState

private const val INSTRUMENT_SANS_URL = "https://github.com/Instrument/instrument-sans"
private const val NEWSREADER_URL = "https://github.com/productiontype/Newsreader"
private const val LUCIDE_URL = "https://github.com/lucide-icons/lucide"

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    hasApiKey: Boolean,
    modelId: String,
    thinkingLevel: ThinkingLevel,
    linuxState: LinuxInstallState,
    linuxSupported: Boolean,
    linuxEnabled: Boolean,
    versionName: String,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onApiKeySave: (String) -> Unit,
    onApiKeyClear: () -> Unit,
    onModelIdChange: (String) -> Unit,
    onThinkingLevelChange: (ThinkingLevel) -> Unit,
    onLinuxInstall: () -> Unit,
    onLinuxReset: () -> Unit,
    onLinuxEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val models = remember { DeepSeekModels.options() }
    val selectedModel = models.firstOrNull { it.id == modelId } ?: models.firstOrNull()
    val thinkingLevels = selectedModel?.thinkingLevels.orEmpty()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showThinkingSheet by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val themeLabel = stringResource(
        when (themeMode) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        },
    )

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(28.dp))

            SettingsGroup(label = stringResource(R.string.settings_group_model)) {
                ApiKeyField(
                    hasApiKey = hasApiKey,
                    onSave = onApiKeySave,
                    onClear = onApiKeyClear,
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.settings_model),
                    value = selectedModel?.displayName ?: modelId,
                    onClick = { showModelSheet = true },
                    showChevron = true,
                    modifier = Modifier.testTag("settings_model"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Brain,
                    title = stringResource(R.string.settings_thinking),
                    value = thinkingLabel(thinkingLevel),
                    enabled = thinkingLevels.size > 1,
                    onClick = if (thinkingLevels.size > 1) {
                        { showThinkingSheet = true }
                    } else {
                        null
                    },
                    showChevron = thinkingLevels.size > 1,
                    modifier = Modifier.testTag("settings_thinking"),
                )
            }

            Spacer(Modifier.height(32.dp))
            LinuxSettingsScreen(
                state = linuxState,
                supported = linuxSupported,
                enabled = linuxEnabled,
                onInstall = onLinuxInstall,
                onReset = onLinuxReset,
                onEnabledChange = onLinuxEnabledChange,
            )

            Spacer(Modifier.height(32.dp))
            SettingsGroup(label = stringResource(R.string.settings_group_appearance)) {
                DdListRow(
                    icon = DdIcons.Moon,
                    title = stringResource(R.string.settings_theme),
                    value = themeLabel,
                    onClick = { showThemeSheet = true },
                    showChevron = true,
                )
            }

            Spacer(Modifier.height(32.dp))
            SettingsGroup(label = stringResource(R.string.settings_group_about)) {
                DdListRow(
                    icon = DdIcons.Info,
                    title = stringResource(R.string.settings_version),
                    value = versionName,
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.File,
                    title = stringResource(R.string.settings_license),
                    onClick = { showLicenses = true },
                    showChevron = true,
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showThemeSheet) {
        ThemeModeSheet(
            current = themeMode,
            onSelect = {
                onThemeModeChange(it)
                showThemeSheet = false
            },
            onClose = { showThemeSheet = false },
        )
    }

    if (showModelSheet) {
        ModelSheet(
            models = models,
            selectedModelId = selectedModel?.id,
            onSelect = {
                onModelIdChange(it)
                showModelSheet = false
            },
            onClose = { showModelSheet = false },
        )
    }

    if (showThinkingSheet) {
        ThinkingSheet(
            levels = thinkingLevels,
            selected = thinkingLevel,
            onSelect = {
                onThinkingLevelChange(it)
                showThinkingSheet = false
            },
            onClose = { showThinkingSheet = false },
        )
    }

    if (showLicenses) {
        LicenseSheet(onClose = { showLicenses = false })
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Column {
        DdSectionLabel(label)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(deepDeepColors().surface)
                .padding(horizontal = 16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ApiKeyField(
    hasApiKey: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = deepDeepColors()
    var draft by remember { mutableStateOf("") }
    var showRequired by remember { mutableStateOf(false) }

    fun submit() {
        val key = draft.trim()
        if (key.isEmpty()) {
            showRequired = !hasApiKey
            return
        }
        showRequired = false
        onSave(key)
        draft = ""
    }

    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
        Text(
            text = stringResource(R.string.settings_api_key_label),
            style = DdType.micro,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(8.dp))
        SettingsTextField(
            value = draft,
            onValueChange = {
                draft = it
                showRequired = false
            },
            placeholder = stringResource(
                if (hasApiKey) R.string.settings_api_key_saved_placeholder else R.string.settings_api_key_hint,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = PasswordVisualTransformation(),
            onDone = { submit() },
        )
        if (showRequired) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_api_key_required),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DdTextAction(
                text = stringResource(R.string.settings_api_key_save),
                onClick = { submit() },
                enabled = draft.isNotBlank(),
            )
            Spacer(Modifier.weight(1f))
            if (hasApiKey) {
                DdTextAction(
                    text = stringResource(R.string.settings_api_key_clear),
                    onClick = {
                        draft = ""
                        showRequired = false
                        onClear()
                    },
                    color = colors.inkTertiary,
                )
            }
        }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onDone: () -> Unit,
) {
    val colors = deepDeepColors()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(
        targetValue = if (focused) colors.accent else colors.hairline,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "settings-field-border",
    )
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.wash)
            .border(1.dp, border, shape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = DdType.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = DdType.body, color = colors.inkTertiary)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ThemeModeSheet(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(stringResource(R.string.settings_theme_title), onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            ThemeMode.entries.forEachIndexed { index, mode ->
                DdListRow(
                    title = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        },
                    ),
                    onClick = { onSelect(mode) },
                    trailing = {
                        if (mode == current) {
                            DdIcon(DdIcons.Check, tint = colors.accent, size = 20.dp)
                        }
                    },
                )
                if (index != ThemeMode.entries.lastIndex) DdHairline()
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ModelSheet(
    models: List<ModelOption>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(stringResource(R.string.settings_model_title), onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            models.forEachIndexed { index, model ->
                DdListRow(
                    title = model.displayName,
                    subtitle = model.id,
                    onClick = { onSelect(model.id) },
                    trailing = {
                        if (model.id == selectedModelId) {
                            DdIcon(DdIcons.Check, tint = colors.accent, size = 20.dp)
                        }
                    },
                )
                if (index != models.lastIndex) DdHairline()
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ThinkingSheet(
    levels: List<ThinkingLevel>,
    selected: ThinkingLevel,
    onSelect: (ThinkingLevel) -> Unit,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(stringResource(R.string.settings_thinking_title), onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            levels.forEachIndexed { index, level ->
                DdListRow(
                    title = thinkingLabel(level),
                    onClick = { onSelect(level) },
                    trailing = {
                        if (level == selected) {
                            DdIcon(DdIcons.Check, tint = colors.accent, size = 20.dp)
                        }
                    },
                )
                if (index != levels.lastIndex) DdHairline()
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LicenseSheet(onClose: () -> Unit) {
    val colors = deepDeepColors()
    val uriHandler = LocalUriHandler.current
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(stringResource(R.string.settings_license_title), onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            LicenseRow(
                title = stringResource(R.string.settings_license_sans),
                subtitle = stringResource(R.string.settings_license_ofl),
                onClick = { uriHandler.openUri(INSTRUMENT_SANS_URL) },
            )
            DdHairline()
            LicenseRow(
                title = stringResource(R.string.settings_license_serif),
                subtitle = stringResource(R.string.settings_license_ofl),
                onClick = { uriHandler.openUri(NEWSREADER_URL) },
            )
            DdHairline()
            LicenseRow(
                title = stringResource(R.string.settings_license_icons),
                subtitle = stringResource(R.string.settings_license_isc),
                onClick = { uriHandler.openUri(LUCIDE_URL) },
            )
            Spacer(Modifier.height(24.dp))
            DdPrimaryButton(
                text = stringResource(R.string.settings_close),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LicenseRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = DdType.body, color = colors.ink)
            Text(text = subtitle, style = DdType.bodySmall, color = colors.inkTertiary)
        }
        DdIcon(DdIcons.External, tint = colors.inkTertiary, size = 16.dp)
    }
}

@Composable
private fun thinkingLabel(level: ThinkingLevel): String = stringResource(
    when (level) {
        ThinkingLevel.OFF -> R.string.settings_thinking_off
        ThinkingLevel.LOW -> R.string.settings_thinking_low
        ThinkingLevel.MEDIUM -> R.string.settings_thinking_medium
        ThinkingLevel.HIGH -> R.string.settings_thinking_high
    },
)
