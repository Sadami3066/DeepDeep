package io.github.whywood.deepdeep.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategyJson
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScanJsonScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val store = remember(context) { ScanStrategyStore(context) }
    var text by remember { mutableStateOf<String?>(null) }
    var invalid by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(store) {
        text = withContext(Dispatchers.IO) { store.sharedText() }
    }

    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
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
            Text(
                text = stringResource(R.string.scan_settings_json_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.scan_settings_json_hint),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(14.dp))
            val current = text
            if (current != null) {
                DdTextField(
                    value = current,
                    onValueChange = {
                        text = it
                        invalid = false
                        skipped = null
                    },
                    placeholder = "[]",
                    singleLine = false,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (invalid) {
                Text(
                    text = stringResource(R.string.scan_settings_json_invalid),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            } else {
                skipped?.let {
                    Text(
                        text = stringResource(R.string.scan_settings_skipped, it),
                        style = DdType.bodySmall,
                        color = colors.warning,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            DdPrimaryButton(
                text = stringResource(R.string.scan_settings_save),
                onClick = {
                    val currentText = text.orEmpty()
                    val parsed = ScanStrategyJson.parse(currentText)
                    if (parsed == null) {
                        invalid = true
                        skipped = null
                    } else {
                        store.saveCustom(parsed.strategies)
                        text = store.sharedText()
                        invalid = false
                        skipped = parsed.skipped.takeIf { it > 0 }
                        Toast.makeText(context, R.string.scan_settings_saved, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = text != null,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
