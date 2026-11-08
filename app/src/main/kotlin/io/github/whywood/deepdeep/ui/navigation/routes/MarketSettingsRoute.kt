package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import io.github.whywood.deepdeep.market.MarketRepository
import io.github.whywood.deepdeep.ui.settings.MarketSettingsScreen

@Composable
internal fun MarketSettingsRoute(repository: MarketRepository, onBack: () -> Unit) {
    MarketSettingsScreen(repository = repository, onBack = onBack)
}
