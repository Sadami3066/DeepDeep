package io.github.whywood.deepdeep.ui.assistant

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.assistant.AssistantEntry
import io.github.whywood.deepdeep.assistant.AssistantOverlayActivity
import io.github.whywood.deepdeep.assistant.AssistantRole
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlinx.coroutines.launch

@Composable
fun AssistantSettingsScreen(
    onBack: () -> Unit,
    onRepairEntry: suspend () -> Result<Unit>,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var roleHeld by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    var currentComponent by remember { mutableStateOf(readAssistantComponent(context)) }
    var repairFailure by remember { mutableStateOf<String?>(null) }
    var repairSucceeded by remember { mutableStateOf(false) }
    val powerUnavailableMessage = stringResource(R.string.assistant_settings_power_unavailable)
    val expectedComponent = remember {
        ComponentName(context, AssistantOverlayActivity::class.java).flattenToShortString()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = AssistantRole.isHeld(context)
        currentComponent = readAssistantComponent(context)
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
                    title = stringResource(R.string.assistant_settings_component),
                    value = currentComponent ?: "—",
                    onClick = null,
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.assistant_settings_expected),
                    value = expectedComponent,
                    onClick = null,
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Refresh,
                    title = stringResource(R.string.assistant_settings_repair),
                    onClick = {
                        scope.launch {
                            val result = onRepairEntry()
                            repairSucceeded = result.isSuccess
                            repairFailure = result.exceptionOrNull()?.let { error ->
                                error.message ?: error.javaClass.simpleName
                            }
                            currentComponent = readAssistantComponent(context)
                        }
                    },
                    showChevron = true,
                    modifier = Modifier.testTag("assistant_repair"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Terminal,
                    title = stringResource(R.string.assistant_settings_power),
                    subtitle = stringResource(R.string.assistant_settings_power_hint),
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
                DdHairline()
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.assistant_settings_preview),
                    onClick = {
                        context.startActivity(
                            Intent(context, AssistantOverlayActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    showChevron = true,
                    modifier = Modifier.testTag("assistant_preview"),
                )
            }
            if (repairFailure != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.assistant_settings_repair_failed, repairFailure.orEmpty()),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            } else if (repairSucceeded) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.assistant_settings_repair_ok),
                    style = DdType.bodySmall,
                    color = colors.success,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.assistant_settings_role_note),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun readAssistantComponent(context: android.content.Context): String? =
    runCatching {
        Settings.Secure.getString(context.contentResolver, "assistant")
    }.getOrNull()?.takeIf { it.isNotBlank() }
