package io.github.whywood.deepdeep.ui.common.market

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.MarketPreset
import io.github.whywood.deepdeep.data.filterMarketPresets
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

internal const val MARKET_TAB_MARKET = 0

@Composable
internal fun MarketPresetSection(
    presets: List<MarketPreset>,
    query: String,
    icon: DdIcon,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val rendered = LinkedHashMap<MarketPreset, String>()
    for (preset in presets) {
        val parts = ArrayList<String>(3)
        for (resId in intArrayOf(preset.nameRes, preset.summaryRes, preset.tagsRes)) {
            parts += stringResource(resId)
        }
        rendered[preset] = parts.joinToString(" ")
    }
    val visible = filterMarketPresets(presets, query) { rendered[it].orEmpty() }
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.market_notice),
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .testTag("${testTagPrefix}_market_notice"),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        if (visible.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.search_no_match),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        } else {
            visible.forEachIndexed { index, preset ->
                if (index > 0) DdHairline()
                MarketPresetRow(
                    preset = preset,
                    icon = icon,
                    testTag = "${testTagPrefix}_market_row_${preset.id}",
                )
            }
        }
    }
}

@Composable
private fun MarketPresetRow(preset: MarketPreset, icon: DdIcon, testTag: String) {
    DdListRow(
        title = stringResource(preset.nameRes),
        subtitle = stringResource(preset.summaryRes) + "\n" + stringResource(preset.tagsRes),
        subtitleMaxLines = 2,
        icon = icon,
        modifier = Modifier.testTag(testTag),
        trailing = {
            DdTextAction(
                text = stringResource(R.string.market_install),
                onClick = {},
                enabled = false,
            )
        },
    )
}
