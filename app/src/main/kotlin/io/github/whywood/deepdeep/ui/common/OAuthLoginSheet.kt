package io.github.whywood.deepdeep.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun OAuthLoginSheet(
    providerName: String,
    session: OAuthLoginSession,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val state by session.state.collectAsStateWithLifecycle()
    var draft by remember(state.prompt) { mutableStateOf("") }

    fun openBrowser(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun submit() {
        val text = draft.trim()
        if (text.isEmpty()) return
        if (session.submitPrompt(text)) draft = ""
    }

    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().testTag("oauth_login_sheet")) {
            DdSheetHeader(
                title = stringResource(R.string.settings_oauth_title, providerName),
                onClose = onDismiss,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                state.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                state.instructions?.takeIf { it.isNotBlank() }?.let { instructions ->
                    Text(
                        text = instructions,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                state.url?.let { url ->
                    DdTextAction(
                        text = stringResource(R.string.settings_oauth_open_browser),
                        onClick = { openBrowser(url) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (state.url != null && !state.finished) {
                    Text(
                        text = stringResource(R.string.settings_oauth_waiting_browser),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (state.options.isNotEmpty()) {
                    state.options.forEachIndexed { index, option ->
                        if (index > 0) DdHairline()
                        DdListRow(
                            title = option.label,
                            subtitle = option.description,
                            onClick = { session.choose(option.id) },
                            modifier = Modifier.testTag("oauth_login_option_${option.id}"),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                state.prompt?.let { prompt ->
                    if (state.url != null) {
                        Text(
                            text = stringResource(R.string.settings_oauth_manual_hint),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = prompt.message,
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    DdTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = prompt.placeholder.orEmpty(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (prompt.secret) KeyboardType.Password else KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        visualTransformation = if (prompt.secret) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        onDone = { submit() },
                        testTag = "oauth_login_prompt",
                    )
                    Spacer(Modifier.height(8.dp))
                    DdPrimaryButton(
                        text = stringResource(R.string.settings_oauth_confirm),
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth().testTag("oauth_login_confirm"),
                        enabled = draft.isNotBlank(),
                    )
                }
                if (state.busy && state.message == null && state.url == null &&
                    state.prompt == null && state.options.isEmpty() && state.error == null &&
                    !state.finished
                ) {
                    Text(
                        text = stringResource(R.string.settings_oauth_waiting),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
                if (!state.finished) {
                    state.error?.takeIf { it.isNotBlank() }?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                DdTextAction(
                    text = stringResource(R.string.settings_oauth_cancel),
                    onClick = {
                        session.cancel()
                        onDismiss()
                    },
                    color = colors.inkTertiary,
                    modifier = Modifier.testTag("oauth_login_cancel"),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
