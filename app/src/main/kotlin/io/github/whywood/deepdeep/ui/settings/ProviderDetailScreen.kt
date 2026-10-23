package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel

@Composable
fun ProviderDetailScreen(
    provider: ProviderSummary,
    models: List<ModelOption>,
    configured: Boolean,
    active: Boolean,
    activeModelId: String?,
    savedApiKey: String? = null,
    onBack: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onActivate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var showModelSheet by remember { mutableStateOf(false) }
    var selectedModelId by remember(provider.id, models) {
        mutableStateOf(
            activeModelId?.takeIf { id -> models.any { it.id == id } } ?: models.firstOrNull()?.id,
        )
    }
    val selectedModel = models.firstOrNull { it.id == selectedModelId }
    val alreadyActive = active && selectedModelId != null && selectedModelId == activeModelId

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
                text = stringResource(R.string.settings_provider_title),
                style = DdType.pageTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            ProviderOverview(provider = provider, configured = configured, active = active)
            Spacer(Modifier.height(28.dp))
            SettingsGroup {
                if (!provider.supportsApiKey) {
                    DdListRow(
                        icon = DdIcons.Info,
                        title = stringResource(R.string.settings_provider_extra_config),
                        subtitle = stringResource(R.string.settings_provider_extra_config_body),
                    )
                }
                if (provider.hasOAuth) {
                    if (!provider.supportsApiKey) DdHairline()
                    DdListRow(
                        icon = DdIcons.Shield,
                        title = stringResource(R.string.settings_provider_oauth_hint),
                        subtitle = stringResource(R.string.settings_provider_oauth_body),
                    )
                }
                if (provider.supportsApiKey) {
                    if (provider.hasOAuth) DdHairline()
                    ApiKeySection(
                        configured = configured,
                        savedApiKey = savedApiKey,
                        onSave = onSaveApiKey,
                        onClear = onClearApiKey,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.settings_provider_model),
                    value = selectedModel?.displayName
                        ?: stringResource(R.string.settings_provider_no_models),
                    onClick = if (models.isNotEmpty()) {
                        { showModelSheet = true }
                    } else {
                        null
                    },
                    showChevron = models.isNotEmpty(),
                    modifier = Modifier.testTag("provider_model"),
                )
            }
            Spacer(Modifier.height(16.dp))
            DdPrimaryButton(
                text = stringResource(
                    if (alreadyActive) R.string.settings_provider_in_use else R.string.settings_provider_activate,
                ),
                onClick = { selectedModelId?.let(onActivate) },
                modifier = Modifier.fillMaxWidth().testTag("provider_activate"),
                enabled = selectedModel != null && !alreadyActive,
            )
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showModelSheet) {
        ModelSheet(
            models = models,
            selectedModelId = selectedModelId,
            onSelect = {
                selectedModelId = it
                showModelSheet = false
            },
            onClose = { showModelSheet = false },
        )
    }
}

@Composable
private fun ProviderOverview(
    provider: ProviderSummary,
    configured: Boolean,
    active: Boolean,
) {
    val colors = deepDeepColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialBadge(provider.name, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.settings_provider_models_count, provider.modelCount),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
            when {
                active -> StatusPill(stringResource(R.string.settings_provider_current), active = true)
                configured -> StatusPill(stringResource(R.string.settings_status_configured), active = false)
                else -> StatusPill(stringResource(R.string.settings_status_unconfigured), active = false)
            }
        }
        DdHairline()
        DdListRow(
            title = stringResource(R.string.settings_provider_model_count),
            value = provider.modelCount.toString(),
        )
        DdHairline()
        DdListRow(
            title = stringResource(R.string.settings_provider_base_url),
            value = provider.baseUrl?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.settings_provider_default_base_url),
        )
    }
}

@Composable
private fun ApiKeySection(
    configured: Boolean,
    savedApiKey: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = deepDeepColors()
    var draft by remember { mutableStateOf("") }
    var showRequired by remember { mutableStateOf(false) }

    fun submit() {
        val key = draft.trim()
        if (key.isEmpty()) {
            showRequired = !configured
            return
        }
        showRequired = false
        draft = ""
        onSave(key)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_provider_api_key),
            style = DdType.micro,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(8.dp))
        DdTextField(
            value = draft,
            onValueChange = {
                draft = it
                showRequired = false
            },
            placeholder = if (configured) {
                maskApiKey(savedApiKey)
            } else {
                stringResource(R.string.settings_provider_api_key_hint)
            },
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
                text = stringResource(R.string.settings_provider_api_key_required),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DdTextAction(
                text = stringResource(R.string.settings_provider_api_key_save),
                onClick = { submit() },
                enabled = draft.isNotBlank(),
            )
            Spacer(Modifier.weight(1f))
            if (configured) {
                DdTextAction(
                    text = stringResource(R.string.settings_provider_api_key_clear),
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
private fun ModelSheet(
    models: List<ModelOption>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(stringResource(R.string.settings_provider_models_title), onClose)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            models.forEachIndexed { index, model ->
                val reasoning = model.thinkingLevels.any { it != ThinkingLevel.OFF }
                DdListRow(
                    title = model.displayName,
                    subtitle = model.id,
                    onClick = { onSelect(model.id) },
                    trailing = {
                        if (reasoning) {
                            StatusPill(
                                text = stringResource(R.string.settings_provider_reasoning),
                                active = false,
                            )
                        }
                        if (model.id == selectedModelId) {
                            Spacer(Modifier.width(8.dp))
                            DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 20.dp)
                        }
                    },
                )
                if (index != models.lastIndex) DdHairline()
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

internal fun maskApiKey(key: String?): String {
    val trimmed = key?.trim().orEmpty()
    return if (trimmed.length >= 6) trimmed.take(3) + "*****" else "*****"
}
