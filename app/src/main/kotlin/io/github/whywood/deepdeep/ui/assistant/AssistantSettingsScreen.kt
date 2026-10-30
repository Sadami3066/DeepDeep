package io.github.whywood.deepdeep.ui.assistant

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.assistant.AssistantEntry
import io.github.whywood.deepdeep.assistant.AssistantRole
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun AssistantSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    var roleHeld by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    val powerUnavailableMessage = stringResource(R.string.assistant_settings_power_unavailable)
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = AssistantRole.isHeld(context)
    }
    LifecycleResumeEffect(Unit) {
        roleHeld = AssistantRole.isHeld(context)
        onPauseOrDispose {}
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("assistant_settings"),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
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
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.assistant_settings_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            DdGroupCard {
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.assistant_settings_role),
                    value = stringResource(
                        if (roleHeld) R.string.assistant_settings_role_held
                        else R.string.assistant_settings_role_missing,
                    ),
                    onClick = {
                        val intent = AssistantRole.requestIntent(context)
                            ?: AssistantRole.settingsIntent(context)
                        roleLauncher.launch(intent)
                    },
                    showChevron = true,
                    modifier = Modifier.testTag("assistant_role"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Terminal,
                    title = stringResource(R.string.assistant_settings_power),
                    onClick = {
                        val intent = AssistantEntry.powerSettingsIntent(context)
                        if (intent == null) {
                            Toast.makeText(
                                context,
                                powerUnavailableMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            runCatching { context.startActivity(intent) }
                        }
                    },
                    showChevron = true,
                    modifier = Modifier.testTag("assistant_power_settings"),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
