package io.github.whywood.deepdeep.ui.purge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSegmented
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdText
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.purge.PurgeAuditEntry
import io.github.whywood.deepdeep.purge.PurgeController
import io.github.whywood.deepdeep.purge.PurgeInjectPosition
import io.github.whywood.deepdeep.purge.PurgeLayer
import io.github.whywood.deepdeep.purge.PurgeLayerId
import io.github.whywood.deepdeep.purge.PurgeLayerState
import io.github.whywood.deepdeep.purge.PurgeSkillInfo
import io.github.whywood.deepdeep.purge.PurgeText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAB_STATUS = 0
private const val TAB_PROMPT = 1
private const val TAB_SKILLS = 2
private const val TAB_DRILL = 3
private const val TAB_MORE = 4

@Composable
fun PurgeScreen(
    controller: PurgeController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_STATUS) }
    var openSkill by remember { mutableStateOf<PurgeSkillInfo?>(null) }
    var skillBody by remember { mutableStateOf("") }
    var probeText by remember { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var draftLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(controller) { controller.start() }
    LaunchedEffect(state.tuning.injection) {
        if (!draftLoaded) {
            draft = state.tuning.injection
            draftLoaded = true
        }
    }
    LaunchedEffect(openSkill) {
        val target = openSkill ?: return@LaunchedEffect
        skillBody = controller.readSkill(target.id).orEmpty()
    }

    val tabs = listOf(
        stringResource(R.string.purge_tab_status),
        stringResource(R.string.purge_tab_prompt),
        stringResource(R.string.purge_tab_skills),
        stringResource(R.string.purge_tab_drill),
        stringResource(R.string.purge_tab_more),
    )

    DdPageScaffold(
        modifier = modifier,
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.purge_back),
            )
            DdText(
                text = stringResource(R.string.purge_title),
                style = DdType.pageTitle,
                modifier = Modifier.padding(start = DdSpacing.Xs),
            )
            Spacer(Modifier.weight(1f))
            DdIconButton(
                icon = DdIcons.Refresh,
                onClick = { controller.refresh() },
                contentDescription = stringResource(R.string.purge_refresh),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.ScreenHorizontal)) {
            DdSegmented(
                options = tabs,
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            Spacer(Modifier.height(DdSpacing.Lg))
        }
        when (tab) {
            TAB_STATUS -> StatusTab(
                layers = state.layers,
                okCount = state.okCount,
                broken = state.brokenCount,
                enabled = state.tuning.enabled,
                skillCount = state.skills.count { it.installed },
                promptBytes = state.promptBytes,
                busy = state.busy,
                onApply = { controller.apply() },
                onRevert = { controller.revert() },
            )

            TAB_PROMPT -> PromptTab(
                draft = draft,
                onDraftChange = { draft = it },
                boost = state.tuning.boost,
                position = state.tuning.injectPosition,
                askNoBlock = state.tuning.askNoBlock,
                writeOverride = state.tuning.writeOverride,
                onSave = { controller.saveInjection(draft) },
                onBoost = { controller.setBoost(it) },
                onPosition = { controller.setInjectPosition(it) },
                onAskNoBlock = { controller.setAskNoBlock(it) },
                onWriteOverride = { controller.setWriteOverride(it) },
                onInsertDefault = { draft = PurgeText.DEFAULT_INJECTION },
            )

            TAB_SKILLS -> SkillsTab(
                skills = state.skills,
                onOpen = { openSkill = it },
                onInstall = { controller.installSkills() },
                onRemove = { controller.removeSkills() },
            )

            TAB_DRILL -> DrillTab(
                skills = state.skills,
                onOpen = { openSkill = it },
            )

            TAB_MORE -> MoreTab(
                overridePath = state.overridePath,
                audit = state.audit,
                probe = state.probe,
                onProbe = { probeText = controller.issueProbe() },
            )
        }
    }

    val target = openSkill
    if (target != null) {
        DdSheet(onDismiss = { openSkill = null }) {
            DdSheetHeader(title = target.id, onClose = { openSkill = null })
            DdText(
                text = target.description,
                style = DdType.bodySmall,
                color = colors.inkSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = DdSpacing.ScreenHorizontal),
            )
            Spacer(Modifier.height(DdSpacing.Sm))
            Row(Modifier.padding(horizontal = DdSpacing.ScreenHorizontal)) {
                DdTextAction(
                    text = stringResource(R.string.purge_copy),
                    onClick = { copyToClipboard(context, skillBody) },
                )
            }
            Spacer(Modifier.height(DdSpacing.Sm))
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DdSpacing.ScreenHorizontal, vertical = DdSpacing.Sm),
            ) {
                DdText(
                    text = skillBody,
                    style = DdType.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.ink,
                )
            }
        }
    }

    val probe = probeText
    if (probe != null) {
        DdSheet(onDismiss = { probeText = null }) {
            DdSheetHeader(
                title = stringResource(R.string.purge_probe_dialog_title),
                onClose = { probeText = null },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.ScreenHorizontal)) {
                DdText(text = probe, style = DdType.bodySmall, color = colors.inkSecondary)
                Spacer(Modifier.height(DdSpacing.Md))
                DdTextAction(
                    text = stringResource(R.string.purge_copy),
                    onClick = { copyToClipboard(context, probe) },
                )
            }
            Spacer(Modifier.height(DdSpacing.Xxl))
        }
    }
}

@Composable
private fun StatusTab(
    layers: List<PurgeLayer>,
    okCount: Int,
    broken: Int,
    enabled: Boolean,
    skillCount: Int,
    promptBytes: Int,
    busy: Boolean,
    onApply: () -> Unit,
    onRevert: () -> Unit,
) {
    val colors = deepDeepColors()
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = DdSpacing.ScreenHorizontal,
            end = DdSpacing.ScreenHorizontal,
            bottom = DdSpacing.Xxxl,
        ),
    ) {
        item {
            DdText(
                text = stringResource(
                    if (enabled) R.string.purge_summary_on else R.string.purge_summary_off,
                    skillCount,
                    promptBytes,
                ),
                style = DdType.body,
            )
            Spacer(Modifier.height(DdSpacing.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(DdSpacing.Md)) {
                DdPrimaryButton(
                    text = stringResource(R.string.purge_apply),
                    onClick = onApply,
                    enabled = !busy,
                )
                DdTextAction(
                    text = stringResource(R.string.purge_revert),
                    onClick = onRevert,
                    enabled = !busy,
                    color = colors.danger,
                )
            }
            Spacer(Modifier.height(DdSpacing.Lg))
            DdHairline()
            Spacer(Modifier.height(DdSpacing.Md))
            DdText(
                text = stringResource(R.string.purge_apply_hint),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(DdSpacing.Md))
            DdSectionLabel(
                text = stringResource(R.string.purge_selfcheck_title, okCount, layers.size, broken),
            )
            Spacer(Modifier.height(DdSpacing.Xs))
        }
        items(layers.size) { index ->
            val layer = layers[index]
            Row(
                Modifier.fillMaxWidth().padding(vertical = DdSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdText(
                    text = layerTitle(layer.id),
                    style = DdType.body,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DdText(
                    text = layerStateLabel(layer.state),
                    style = DdType.chip,
                    color = when (layer.state) {
                        PurgeLayerState.OK -> colors.success
                        PurgeLayerState.PENDING -> colors.warning
                        PurgeLayerState.BROKEN -> colors.danger
                        PurgeLayerState.NOT_APPLICABLE -> colors.inkTertiary
                    },
                )
                Spacer(Modifier.width(DdSpacing.Sm))
                DdText(text = layer.evidence, style = DdType.label, color = colors.inkTertiary)
            }
            DdHairline()
        }
    }
}

@Composable
private fun PromptTab(
    draft: String,
    onDraftChange: (String) -> Unit,
    boost: Boolean,
    position: PurgeInjectPosition,
    askNoBlock: Boolean,
    writeOverride: Boolean,
    onSave: () -> Unit,
    onBoost: (Boolean) -> Unit,
    onPosition: (PurgeInjectPosition) -> Unit,
    onAskNoBlock: (Boolean) -> Unit,
    onWriteOverride: (Boolean) -> Unit,
    onInsertDefault: () -> Unit,
) {
    val colors = deepDeepColors()
    val positions = PurgeInjectPosition.entries
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = DdSpacing.ScreenHorizontal,
            end = DdSpacing.ScreenHorizontal,
            bottom = DdSpacing.Xxxl,
        ),
    ) {
        item {
            DdTextArea(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = stringResource(R.string.purge_prompt_hint),
            )
            Spacer(Modifier.height(DdSpacing.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(DdSpacing.Lg)) {
                DdPrimaryButton(text = stringResource(R.string.purge_prompt_save), onClick = onSave)
                DdTextAction(text = stringResource(R.string.purge_prompt_default), onClick = onInsertDefault)
            }
            Spacer(Modifier.height(DdSpacing.Lg))
            DdHairline()
            Spacer(Modifier.height(DdSpacing.Md))
            SwitchRow(
                title = stringResource(R.string.purge_boost_title),
                subtitle = stringResource(R.string.purge_boost_subtitle),
                checked = boost,
                onCheckedChange = onBoost,
            )
            DdHairline()
            SwitchRow(
                title = stringResource(R.string.purge_question_title),
                subtitle = stringResource(R.string.purge_question_subtitle),
                checked = askNoBlock,
                onCheckedChange = onAskNoBlock,
            )
            DdHairline()
            SwitchRow(
                title = stringResource(R.string.purge_override_title),
                subtitle = stringResource(R.string.purge_override_subtitle),
                checked = writeOverride,
                onCheckedChange = onWriteOverride,
            )
            DdHairline()
            Spacer(Modifier.height(DdSpacing.Md))
            DdSectionLabel(text = stringResource(R.string.purge_position_title))
            Spacer(Modifier.height(DdSpacing.Sm))
            DdSegmented(
                options = positions.map { positionLabel(it) },
                selectedIndex = positions.indexOf(position).coerceAtLeast(0),
                onSelect = { index -> onPosition(positions[index]) },
            )
            Spacer(Modifier.height(DdSpacing.Md))
            DdText(
                text = stringResource(R.string.purge_position_hint),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = DdSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            DdText(text = title, style = DdType.body)
            DdText(text = subtitle, style = DdType.bodySmall, color = deepDeepColors().inkTertiary)
        }
        Spacer(Modifier.width(DdSpacing.Md))
        DdSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SkillsTab(
    skills: List<PurgeSkillInfo>,
    onOpen: (PurgeSkillInfo) -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val installed = skills.count { it.installed }
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = DdSpacing.ScreenHorizontal,
            end = DdSpacing.ScreenHorizontal,
            bottom = DdSpacing.Xxxl,
        ),
    ) {
        item {
            DdText(
                text = stringResource(R.string.purge_skills_summary, installed, skills.size),
                style = DdType.bodySmall,
                color = deepDeepColors().inkSecondary,
            )
            Spacer(Modifier.height(DdSpacing.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(DdSpacing.Lg)) {
                DdPrimaryButton(text = stringResource(R.string.purge_skills_install), onClick = onInstall)
                DdTextAction(
                    text = stringResource(R.string.purge_skills_remove),
                    onClick = onRemove,
                    color = deepDeepColors().danger,
                )
            }
            Spacer(Modifier.height(DdSpacing.Md))
            DdHairline()
        }
        items(skills.size) { index ->
            val skill = skills[index]
            DdListRow(
                title = skill.id,
                subtitle = skill.description,
                value = stringResource(
                    if (skill.installed) R.string.purge_skills_installed else R.string.purge_skills_pending,
                ),
                subtitleMaxLines = 2,
                showChevron = true,
                onClick = { onOpen(skill) },
            )
            DdHairline()
        }
    }
}

@Composable
private fun DrillTab(
    skills: List<PurgeSkillInfo>,
    onOpen: (PurgeSkillInfo) -> Unit,
) {
    val groups = listOf(
        "recon" to stringResource(R.string.purge_drill_recon),
        "vuln-scan" to stringResource(R.string.purge_drill_vuln_scan),
        "exploit" to stringResource(R.string.purge_drill_exploit),
        "internal" to stringResource(R.string.purge_drill_internal),
        "plan" to stringResource(R.string.purge_drill_plan),
    )
    val others = skills.filter { skill -> groups.none { it.first == skill.role.lowercase() } }
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = DdSpacing.ScreenHorizontal,
            end = DdSpacing.ScreenHorizontal,
            bottom = DdSpacing.Xxxl,
        ),
    ) {
        item {
            DdText(
                text = stringResource(R.string.purge_drill_hint),
                style = DdType.bodySmall,
                color = deepDeepColors().inkSecondary,
            )
            Spacer(Modifier.height(DdSpacing.Md))
        }
        groups.forEach { (role, label) ->
            val members = skills.filter { it.role.lowercase() == role }
            if (members.isEmpty()) return@forEach
            item {
                DdSectionLabel(text = "$label · ${members.size}")
                Spacer(Modifier.height(DdSpacing.Xs))
            }
            items(members.size) { index ->
                val skill = members[index]
                DdListRow(
                    title = skill.id,
                    subtitle = skill.description,
                    subtitleMaxLines = 2,
                    showChevron = true,
                    onClick = { onOpen(skill) },
                )
                DdHairline()
            }
        }
        if (others.isNotEmpty()) {
            item {
                DdSectionLabel(text = stringResource(R.string.purge_drill_other))
                Spacer(Modifier.height(DdSpacing.Xs))
            }
            items(others.size) { index ->
                val skill = others[index]
                DdListRow(
                    title = skill.id,
                    subtitle = skill.description,
                    subtitleMaxLines = 2,
                    showChevron = true,
                    onClick = { onOpen(skill) },
                )
                DdHairline()
            }
        }
    }
}

@Composable
private fun MoreTab(
    overridePath: String?,
    audit: List<PurgeAuditEntry>,
    probe: String?,
    onProbe: () -> Unit,
) {
    val colors = deepDeepColors()
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = DdSpacing.ScreenHorizontal,
            end = DdSpacing.ScreenHorizontal,
            bottom = DdSpacing.Xxxl,
        ),
    ) {
        item {
            DdListRow(
                title = stringResource(R.string.purge_more_override),
                subtitle = overridePath ?: stringResource(R.string.purge_more_override_absent),
                subtitleMaxLines = 2,
            )
            DdHairline()
            DdListRow(
                title = stringResource(R.string.purge_more_probe),
                subtitle = probe?.let { "purge-$it" } ?: stringResource(R.string.purge_more_probe_idle),
                showChevron = true,
                onClick = onProbe,
            )
            DdHairline()
            DdListRow(
                title = stringResource(R.string.purge_more_audit),
                value = audit.size.toString(),
            )
            Spacer(Modifier.height(DdSpacing.Md))
            DdSectionLabel(text = stringResource(R.string.purge_more_recent))
            Spacer(Modifier.height(DdSpacing.Xs))
        }
        items(audit.size) { index ->
            val entry = audit[index]
            Row(
                Modifier.fillMaxWidth().padding(vertical = DdSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdText(
                    text = formatter.format(Date(entry.at)),
                    style = DdType.label,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.width(DdSpacing.Md))
                DdText(
                    text = entry.action,
                    style = DdType.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DdText(
                    text = entry.detail,
                    style = DdType.label,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DdHairline()
        }
    }
}

@Composable
private fun layerTitle(id: PurgeLayerId): String = stringResource(
    when (id) {
        PurgeLayerId.PROMPT_PREAMBLE -> R.string.purge_layer_preamble
        PurgeLayerId.PROMPT_RULES -> R.string.purge_layer_rules
        PurgeLayerId.BOOST -> R.string.purge_layer_boost
        PurgeLayerId.INJECT_POSITION -> R.string.purge_layer_position
        PurgeLayerId.HOST_RULES -> R.string.purge_layer_host_rules
        PurgeLayerId.QUESTION -> R.string.purge_layer_question
        PurgeLayerId.INPUT_ROUTING -> R.string.purge_layer_input_routing
        PurgeLayerId.SKILL_ASSETS -> R.string.purge_layer_skill_assets
        PurgeLayerId.SKILLS -> R.string.purge_layer_skills
        PurgeLayerId.OVERRIDE_DOC -> R.string.purge_layer_override
        PurgeLayerId.AUDIT -> R.string.purge_layer_audit
        PurgeLayerId.PROBE -> R.string.purge_layer_probe
    },
)

@Composable
private fun layerStateLabel(state: PurgeLayerState): String = stringResource(
    when (state) {
        PurgeLayerState.OK -> R.string.purge_state_ok
        PurgeLayerState.PENDING -> R.string.purge_state_pending
        PurgeLayerState.BROKEN -> R.string.purge_state_broken
        PurgeLayerState.NOT_APPLICABLE -> R.string.purge_state_na
    },
)

@Composable
private fun positionLabel(position: PurgeInjectPosition): String = stringResource(
    when (position) {
        PurgeInjectPosition.HEAD -> R.string.purge_position_head
        PurgeInjectPosition.TAIL -> R.string.purge_position_tail
        PurgeInjectPosition.BOTH -> R.string.purge_position_both
    },
)

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("purge", text))
}
