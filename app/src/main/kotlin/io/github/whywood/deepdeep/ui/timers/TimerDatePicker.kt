package io.github.whywood.deepdeep.ui.timers

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val CELL_HEIGHT = 44.dp
private val TODAY_OUTLINE_ALPHA = 0.35f

@Composable
fun DdDatePicker(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] }
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("yyyy MMMM", locale) }
    val weekdayNames = remember(locale) {
        DayOfWeek.entries.map { day -> day.getDisplayName(TextStyle.NARROW, locale) }
    }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val today = remember { LocalDate.now() }

    DdSheet(onDismiss = onDismiss) {
        TimerPickerHeader(title = stringResource(R.string.timers_date_label), onDismiss = onDismiss)
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = DdSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ChevronRight,
                onClick = { month = month.minusMonths(1) },
                contentDescription = stringResource(R.string.timers_prev_month),
                modifier = Modifier.rotate(180f),
                tint = colors.inkSecondary,
                iconSize = 18.dp,
            )
            Text(
                text = monthFormatter.format(month.atDay(1)),
                modifier = Modifier.weight(1f),
                style = DdType.body,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            DdIconButton(
                icon = DdIcons.ChevronRight,
                onClick = { month = month.plusMonths(1) },
                contentDescription = stringResource(R.string.timers_next_month),
                tint = colors.inkSecondary,
                iconSize = 18.dp,
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Md)) {
            weekdayNames.forEach { name ->
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(DdSpacing.Xs))
        Crossfade(
            targetState = month,
            animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
            label = "timer-date-month",
        ) { currentMonth ->
            MonthGrid(
                month = currentMonth,
                selected = selected,
                today = today,
                onSelect = onSelect,
                onDismiss = onDismiss,
            )
        }
        Spacer(Modifier.height(DdSpacing.Lg))
    }
}

@Composable
internal fun TimerPickerHeader(title: String, onDismiss: () -> Unit) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = DdSpacing.Xxl, end = DdSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = DdType.heading,
            color = colors.ink,
        )
        DdIconButton(
            icon = DdIcons.Close,
            onClick = onDismiss,
            contentDescription = stringResource(R.string.timers_close),
            tint = colors.inkSecondary,
            iconSize = 20.dp,
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val dates = (0 until 42).map { index ->
        val dayOfMonth = index - leadingBlanks + 1
        if (dayOfMonth in 1..daysInMonth) month.atDay(dayOfMonth) else null
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Md)) {
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier.weight(1f).height(CELL_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                selected = date == selected,
                                today = today,
                                onClick = {
                                    onSelect(date)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val interaction = remember { MutableInteractionSource() }
    val isToday = date == today
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.ink) else Modifier)
            .then(
                if (!selected && isToday) {
                    Modifier.border(1.dp, colors.ink.copy(alpha = TODAY_OUTLINE_ALPHA), CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = DdType.bodySmall,
            color = if (selected) colors.paper else colors.inkSecondary,
        )
    }
}
