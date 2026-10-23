package io.github.whywood.deepdeep.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.PersonalizationUi
import io.github.whywood.deepdeep.data.TonePreset
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSegmentedControl
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.InteractionStyle
import kotlinx.coroutines.delay

@Composable
fun PersonalizationScreen(
    value: PersonalizationUi,
    onBack: () -> Unit,
    onSave: (PersonalizationUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var initialized by rememberSaveable { mutableStateOf(false) }
    var edited by rememberSaveable { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf(value.nickname) }
    var interaction by rememberSaveable { mutableStateOf(value.interaction) }
    var tone by rememberSaveable { mutableStateOf(value.tone) }
    var instruction by rememberSaveable { mutableStateOf(value.instruction) }

    val warmText = stringResource(R.string.personalization_preset_warm)
    val cuteText = stringResource(R.string.personalization_preset_cute)
    val calmText = stringResource(R.string.personalization_preset_calm)
    val toneTexts = mapOf(
        TonePreset.WARM to warmText,
        TonePreset.CUTE to cuteText,
        TonePreset.CALM to calmText,
    )

    LaunchedEffect(value) {
        if (!initialized) {
            nickname = value.nickname
            interaction = value.interaction
            tone = value.tone
            instruction = value.instruction
            initialized = true
        }
    }

    fun currentValue() = PersonalizationUi(
        nickname = nickname,
        interaction = interaction,
        tone = tone,
        instruction = instruction,
    )

    LaunchedEffect(nickname, instruction, edited) {
        if (!edited) return@LaunchedEffect
        delay(300)
        onSave(currentValue())
    }

    fun flushAndBack() {
        if (edited) onSave(currentValue())
        onBack()
    }

    BackHandler(onBack = ::flushAndBack)

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
                onClick = ::flushAndBack,
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
                text = stringResource(R.string.settings_personalization),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))

            DdSectionLabel(
                stringResource(R.string.personalization_nickname_title),
                Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    DdTextField(
                        value = nickname,
                        onValueChange = {
                            nickname = it.take(PersonalizationStore.MAX_NICKNAME_LENGTH)
                            edited = true
                        },
                        placeholder = stringResource(R.string.personalization_nickname_hint),
                        testTag = "personalization_nickname",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.personalization_nickname_description),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            DdSectionLabel(
                stringResource(R.string.personalization_interaction_title),
                Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    DdSegmentedControl(
                        options = InteractionStyle.entries,
                        selected = interaction,
                        onSelect = {
                            interaction = it
                            edited = true
                            onSave(currentValue())
                        },
                        modifier = Modifier.fillMaxWidth().testTag("personalization_interaction"),
                        label = { interactionLabel(it) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = interactionDescription(interaction),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            DdSectionLabel(
                stringResource(R.string.personalization_tone_title),
                Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    DdSegmentedControl(
                        options = TonePreset.entries,
                        selected = tone,
                        onSelect = { selected ->
                            tone = selected
                            if (selected != TonePreset.CUSTOM) {
                                instruction = toneTexts[selected].orEmpty()
                            }
                            edited = true
                            onSave(currentValue())
                        },
                        modifier = Modifier.fillMaxWidth().testTag("personalization_tone"),
                        label = { toneLabel(it) },
                    )
                    Spacer(Modifier.height(12.dp))
                    DdTextArea(
                        value = instruction,
                        onValueChange = { next ->
                            instruction = next
                            if (tone != TonePreset.CUSTOM && instruction != toneTexts[tone]) {
                                tone = TonePreset.CUSTOM
                            }
                            edited = true
                        },
                        placeholder = stringResource(R.string.personalization_instruction_hint),
                        testTag = "personalization_instruction",
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.personalization_preset_hint),
                            modifier = Modifier.weight(1f),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                        Text(
                            text = stringResource(
                                R.string.personalization_instruction_counter,
                                instruction.length,
                            ),
                            style = DdType.numeric,
                            color = colors.inkTertiary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun interactionLabel(style: InteractionStyle): String = stringResource(
    when (style) {
        InteractionStyle.QUIET -> R.string.personalization_interaction_quiet
        InteractionStyle.BALANCED -> R.string.personalization_interaction_balanced
        InteractionStyle.PROACTIVE -> R.string.personalization_interaction_proactive
    },
)

@Composable
private fun interactionDescription(style: InteractionStyle): String = stringResource(
    when (style) {
        InteractionStyle.QUIET -> R.string.personalization_interaction_quiet_body
        InteractionStyle.BALANCED -> R.string.personalization_interaction_balanced_body
        InteractionStyle.PROACTIVE -> R.string.personalization_interaction_proactive_body
    },
)

@Composable
private fun toneLabel(tone: TonePreset): String = stringResource(
    when (tone) {
        TonePreset.WARM -> R.string.personalization_tone_warm
        TonePreset.CUTE -> R.string.personalization_tone_cute
        TonePreset.CALM -> R.string.personalization_tone_calm
        TonePreset.CUSTOM -> R.string.personalization_tone_custom
    },
)
