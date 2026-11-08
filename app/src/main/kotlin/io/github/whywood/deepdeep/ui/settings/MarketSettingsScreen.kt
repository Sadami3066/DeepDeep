package io.github.whywood.deepdeep.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdRowDivider
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.market.MarketDefaults
import io.github.whywood.deepdeep.market.MarketRepository
import io.github.whywood.deepdeep.market.MarketState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val MarketDividerInset = 50.dp

@Composable
fun MarketSettingsScreen(
    repository: MarketRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(false) }
    val updatedText = stringResource(R.string.market_settings_updated)
    val failedText = stringResource(R.string.market_settings_failed)
    val lastUpdate = (state as? MarketState.Ready)
        ?.fetchedAt
        ?.takeIf { it > 0L }
        ?.let { formatMarketTime(it) }

    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.market_settings_title),
                style = DdType.heading,
                color = colors.ink,
            )
        },
        modifier = modifier,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            DdGroupCard {
                DdListRow(
                    icon = DdIcons.Globe,
                    title = stringResource(R.string.market_settings_source_label),
                    value = MarketDefaults.REGISTRY_URL,
                    modifier = Modifier.testTag("market_settings_source"),
                )
                DdRowDivider(leadingInset = MarketDividerInset)
                DdListRow(
                    icon = DdIcons.Refresh,
                    title = stringResource(R.string.market_settings_check),
                    value = if (checking) stringResource(R.string.market_settings_checking) else null,
                    onClick = {
                        if (!checking) {
                            scope.launch {
                                checking = true
                                val updated = runCatching { repository.refresh(force = true) }
                                    .getOrDefault(false)
                                checking = false
                                Toast.makeText(
                                    context,
                                    if (updated) updatedText else failedText,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.testTag("market_settings_check"),
                )
                DdRowDivider(leadingInset = MarketDividerInset)
                DdListRow(
                    icon = DdIcons.Clock,
                    title = stringResource(R.string.market_settings_last_update),
                    value = lastUpdate ?: stringResource(R.string.market_settings_never),
                    modifier = Modifier.testTag("market_settings_updated_at"),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun formatMarketTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
