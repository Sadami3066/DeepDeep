package io.github.whywood.deepdeep.ui.timers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.deepDeepColors
import java.time.LocalTime

private val ROW_HEIGHT = 44.dp
private const val VISIBLE_ROWS = 5

private val TIME_TEXT_STYLE = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 20.sp,
    lineHeight = 26.sp,
)

@Composable
fun DdTimePicker(
    selected: LocalTime,
    onSelect: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    var hour by remember { mutableStateOf(selected.hour) }
    var minute by remember { mutableStateOf(selected.minute) }

    DdSheet(onDismiss = onDismiss) {
        TimerPickerHeader(title = stringResource(R.string.timers_time_label), onDismiss = onDismiss)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TimeColumn(
                values = 0..23,
                selected = hour,
                onSelect = { value ->
                    hour = value
                    onSelect(LocalTime.of(value, minute))
                },
                modifier = Modifier.weight(1f),
            )
            TimeColumn(
                values = 0..59,
                selected = minute,
                onSelect = { value ->
                    minute = value
                    onSelect(LocalTime.of(hour, value))
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdTextAction(
                text = stringResource(R.string.timers_done),
                onClick = onDismiss,
                color = colors.ink,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TimeColumn(
    values: IntRange,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val count = values.last - values.first + 1
    val maxFirstVisible = (count - VISIBLE_ROWS).coerceAtLeast(0)
    val initialIndex = (selected - VISIBLE_ROWS / 2).coerceIn(0, maxFirstVisible)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    LazyColumn(
        state = state,
        modifier = modifier.height(ROW_HEIGHT * VISIBLE_ROWS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(values.toList(), key = { it }) { value ->
            val isSelected = value == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (isSelected) Modifier.background(colors.ink) else Modifier)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(value) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toString().padStart(2, '0'),
                    style = TIME_TEXT_STYLE,
                    color = if (isSelected) colors.paper else colors.inkSecondary,
                )
            }
        }
    }
}
