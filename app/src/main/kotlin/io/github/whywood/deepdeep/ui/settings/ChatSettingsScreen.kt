package io.github.whywood.deepdeep.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.CHAT_FONT_SCALE_MAX
import io.github.whywood.deepdeep.data.CHAT_FONT_SCALE_MIN
import io.github.whywood.deepdeep.data.SendWhileRunning
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.data.UiToggle
import io.github.whywood.deepdeep.data.normalizeChatFontScale
import kotlin.math.roundToInt
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun ChatSettingsScreen(
    preferences: UiPreferences,
    sendWhileRunning: SendWhileRunning = SendWhileRunning.STEER,
    onBack: () -> Unit,
    onToggle: (UiToggle, Boolean) -> Unit,
    voiceAutoSpeak: Boolean = false,
    onVoiceAutoSpeakChange: (Boolean) -> Unit = {},
    onChatFontScaleChange: (Float) -> Unit = {},
    onSendWhileRunningChange: (SendWhileRunning) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = DdSpacing.Sm),
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
                .padding(horizontal = DdSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(R.string.settings_chat),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            SettingsGroup {
                ChatFontSizeRow(
                    scale = preferences.chatFontScale,
                    onChange = onChatFontScaleChange,
                )
                DdHairline()
                SwitchRow(
                    title = R.string.settings_auto_collapse_process,
                    checked = preferences.autoCollapseProcess,
                    onCheckedChange = { onToggle(UiToggle.AUTO_COLLAPSE_PROCESS, it) },
                )
                DdHairline()
                SendWhileRunningRow(
                    value = sendWhileRunning,
                    onChange = onSendWhileRunningChange,
                )
                DdHairline()
                SwitchRow(
                    title = R.string.settings_auto_retry,
                    checked = preferences.autoRetry,
                    onCheckedChange = { onToggle(UiToggle.AUTO_RETRY, it) },
                )
                DdHairline()
                SwitchRow(
                    title = R.string.settings_background_job_wake,
                    checked = preferences.backgroundJobWake,
                    onCheckedChange = { onToggle(UiToggle.BACKGROUND_JOB_WAKE, it) },
                )
                DdHairline()
                SwitchRow(
                    title = R.string.voice_setting_auto_speak,
                    checked = voiceAutoSpeak,
                    onCheckedChange = onVoiceAutoSpeakChange,
                )
            }
            Spacer(Modifier.height(DdSpacing.Huge))
        }
    }
}

@Composable
private fun SendWhileRunningRow(
    value: SendWhileRunning,
    onChange: (SendWhileRunning) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = deepDeepColors()
    Box {
        DdListRow(
            title = stringResource(R.string.settings_send_while_running),
            value = sendWhileRunningLabel(value),
            onClick = { expanded = true },
            showChevron = true,
            modifier = Modifier.testTag("settings_send_while_running"),
        )
        DdMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            menuWidth = 356.dp,
        ) {
            SendWhileRunningMenuItem(
                selected = value == SendWhileRunning.STEER,
                label = stringResource(R.string.settings_send_while_running_steer),
                onClick = {
                    expanded = false
                    onChange(SendWhileRunning.STEER)
                },
                testTag = "settings_send_while_running_option_steer",
            )
            SendWhileRunningMenuItem(
                selected = value == SendWhileRunning.QUEUE,
                label = stringResource(R.string.settings_send_while_running_queue),
                onClick = {
                    expanded = false
                    onChange(SendWhileRunning.QUEUE)
                },
                testTag = "settings_send_while_running_option_queue",
            )
        }
    }
}

@Composable
private fun SendWhileRunningMenuItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    DdMenuItem(
        text = label,
        onClick = onClick,
        selected = selected,
        testTag = testTag,
    )
}

@Composable
private fun sendWhileRunningLabel(value: SendWhileRunning): String = stringResource(
    when (value) {
        SendWhileRunning.STEER -> R.string.settings_send_while_running_steer
        SendWhileRunning.QUEUE -> R.string.settings_send_while_running_queue
    },
)

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val stateDescription = stringResource(
        if (checked) R.string.settings_switch_on else R.string.settings_switch_off,
    )
    DdListRow(
        title = stringResource(title),
        onClick = { onCheckedChange(!checked) },
        trailing = {
            DdSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                stateDescription = stateDescription,
            )
        },
    )
}


@Composable
private fun ChatFontSizeRow(scale: Float, onChange: (Float) -> Unit) {
    val colors = deepDeepColors()
    var sliderValue by remember(scale) { mutableFloatStateOf(normalizeChatFontScale(scale)) }
    val percent = (sliderValue * 100f).roundToInt()
    Column(
        Modifier.fillMaxWidth().padding(
            horizontal = LocalDdRowHorizontalPadding.current,
            vertical = DdSpacing.Md,
        ),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.chat_font_size_title),
                modifier = Modifier.weight(1f),
                style = DdType.body,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.chat_font_size_value, percent),
                style = DdType.numeric,
                color = colors.inkSecondary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = normalizeChatFontScale(it) },
            onValueChangeFinished = { onChange(sliderValue) },
            valueRange = CHAT_FONT_SCALE_MIN..CHAT_FONT_SCALE_MAX,
            steps = 10,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.wash,
            ),
            modifier = Modifier.fillMaxWidth().testTag("chat_font_size_slider"),
        )
        Text(
            text = stringResource(R.string.chat_font_size_preview),
            modifier = Modifier.padding(vertical = DdSpacing.Xs),
            style = DdType.body.copy(
                fontSize = (16f * sliderValue).sp,
                lineHeight = (26f * sliderValue).sp,
            ),
            color = colors.inkSecondary,
        )
    }
}
