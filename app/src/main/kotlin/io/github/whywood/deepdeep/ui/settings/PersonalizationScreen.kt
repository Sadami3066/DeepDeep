package io.github.whywood.deepdeep.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.PersonalizationUi
import io.github.whywood.deepdeep.data.ProfileAvatarStore
import io.github.whywood.deepdeep.data.TonePreset
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSegmentedControl
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.InteractionStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@Composable
fun PersonalizationScreen(
    value: PersonalizationUi,
    onBack: () -> Unit,
    onSave: (PersonalizationUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarStore = remember(context) { ProfileAvatarStore(context) }
    var avatarStamp by remember { mutableLongStateOf(avatarStore.current()?.lastModified() ?: 0L) }
    val avatarFile = remember(avatarStamp) { avatarStore.current() }
    val avatarBitmap by produceState<ImageBitmap?>(initialValue = null, avatarStamp) {
        this.value = withContext(Dispatchers.IO) { avatarStore.load()?.asImageBitmap() }
    }
    val avatarFailedText = stringResource(R.string.personalization_avatar_failed)
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) { avatarStore.save(uri) }
            if (saved) {
                avatarStamp = avatarStore.current()?.lastModified() ?: 0L
            } else {
                Toast.makeText(context, avatarFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun removeAvatar() {
        scope.launch {
            val cleared = withContext(Dispatchers.IO) { avatarStore.clear() }
            if (cleared) {
                avatarStamp = avatarStore.current()?.lastModified() ?: 0L
            } else {
                Toast.makeText(context, avatarFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }
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

            DdGroupCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(colors.wash),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = avatarBitmap
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            DdIcon(icon = DdIcons.Image, tint = colors.inkTertiary, size = 22.dp)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.personalization_avatar_title),
                            style = DdType.body,
                            color = colors.ink,
                        )
                        if (avatarFile == null) {
                            Text(
                                text = stringResource(R.string.personalization_avatar_empty),
                                style = DdType.bodySmall,
                                color = colors.inkTertiary,
                            )
                        }
                    }
                    DdTextAction(
                        text = stringResource(
                            if (avatarFile == null) {
                                R.string.personalization_avatar_pick
                            } else {
                                R.string.personalization_avatar_change
                            },
                        ),
                        onClick = {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        modifier = Modifier.testTag("personalization_avatar_pick"),
                    )
                }
                if (avatarFile != null) {
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.personalization_avatar_remove),
                        onClick = { removeAvatar() },
                        modifier = Modifier.testTag("personalization_avatar_remove"),
                    )
                }
            }

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
