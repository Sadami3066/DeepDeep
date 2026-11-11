package io.github.whywood.deepdeep.ui.common.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.market.MarketRequiredAccess

@Composable
internal fun MarketAccessTag(tier: MarketRequiredAccess, modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    val tint = when (tier) {
        MarketRequiredAccess.Normal -> colors.success
        MarketRequiredAccess.Adb -> colors.warning
        MarketRequiredAccess.Root -> colors.danger
    }
    val label = stringResource(
        when (tier) {
            MarketRequiredAccess.Normal -> R.string.market_access_normal
            MarketRequiredAccess.Adb -> R.string.market_access_adb
            MarketRequiredAccess.Root -> R.string.market_access_root
        },
    )
    Text(
        text = label,
        modifier = modifier
            .clip(DdShapes.Small)
            .background(tint.copy(alpha = DdAlpha.TrackLight))
            .padding(horizontal = DdSpacing.Sm, vertical = DdSpacing.Xxs),
        style = DdType.chip,
        color = tint,
        maxLines = 1,
    )
}
