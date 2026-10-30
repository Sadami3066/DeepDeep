package io.github.whywood.deepdeep.ui.mcp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pi.ai.auth.oauth.OAuthCallbackServer
import dev.pi.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpOAuthService
import io.github.whywood.deepdeep.runtime.McpProbeResult
import io.github.whywood.deepdeep.runtime.McpProbeState
import io.github.whywood.deepdeep.runtime.McpServerProbe
import io.github.whywood.deepdeep.runtime.parseMcpEntries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class McpEditorTarget(
    val name: String = "",
)

@Composable
internal fun McpEditorSheet(
    target: McpEditorTarget,
    store: McpConfigStore,
    probe: McpServerProbe,
    oauth: McpOAuthService,
    onDismiss: () -> Unit,
    onReloadSessions: suspend () -> Unit,
    onReconnect: suspend (String) -> Unit,
    onCommitted: () -> Unit,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(target.name) { mutableStateOf("") }
    var loaded by rememberSaveable(target.name) { mutableStateOf(false) }
    var missing by remember(target.name) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<McpProbeResult?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(target.name) {
        if (!loaded && !missing) {
            val stored = store.entryJson(target.name)
            if (stored == null) {
                missing = true
                error = "服务器配置不存在"
            } else {
                text = stored
                loaded = true
            }
        }
    }

    val parsed = if (loaded) {
        runCatching { parseMcpEntries(text).singleOrNull() }.getOrNull()
    } else {
        null
    }
    val http = parsed?.config as? McpServerConfig.Http
    val hasAuthorization = http?.headers?.keys?.any { it.equals("Authorization", ignoreCase = true) } == true

    fun save() {
        if (saving || !loaded || missing) return
        val body = text
        error = null
        saving = true
        scope.launch {
            val result = runCatching {
                store.updateFromJson(target.name, body)
                onReloadSessions()
            }
            saving = false
            result.fold(
                onSuccess = {
                    onCommitted()
                    onDismiss()
                },
                onFailure = { failure -> error = failure.message ?: "保存失败" },
            )
        }
    }

    fun test() {
        val entry = parsed ?: run {
            error = "JSON 配置不正确"
            return
        }
        if (testing || saving) return
        error = null
        testing = true
        testResult = null
        scope.launch {
            testResult = probe.probe(entry.name, entry.config)
            testing = false
        }
    }

    fun delete() {
        if (saving) return
        saving = true
        scope.launch {
            val result = runCatching {
                store.remove(target.name)
                onReloadSessions()
            }
            saving = false
            if (result.isSuccess) {
                onCommitted()
                onDismiss()
            } else {
                error = "删除失败"
            }
        }
    }

    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).testTag("mcp_editor_sheet")) {
            DdSheetHeader(
                title = stringResource(R.string.mcp_edit_title, target.name),
                onClose = onDismiss,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                if (missing) {
                    Text(
                        text = stringResource(R.string.mcp_error_load_failed),
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                } else if (!loaded) {
                    DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                } else {
                    DdTextArea(
                        value = text,
                        onValueChange = {
                            text = it
                            error = null
                            testResult = null
                        },
                        placeholder = "",
                        minHeight = 280.dp,
                        maxHeight = 420.dp,
                        testTag = "mcp_config_json",
                    )
                    error?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier.padding(top = 12.dp),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                    testResult?.let { result ->
                        Spacer(Modifier.height(12.dp))
                        TestResultLine(result)
                    }
                    if (http != null && !hasAuthorization) {
                        Spacer(Modifier.height(24.dp))
                        DdHairline()
                        Spacer(Modifier.height(16.dp))
                        OAuthSection(
                            serverName = target.name,
                            config = http,
                            oauth = oauth,
                            onAuthenticated = {
                                scope.launch {
                                    onReconnect(target.name)
                                    onCommitted()
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DdTextAction(
                        text = stringResource(R.string.mcp_test),
                        onClick = { test() },
                        modifier = Modifier.testTag("mcp_test"),
                        enabled = !testing && !saving && loaded && !missing,
                    )
                    if (testing) {
                        Spacer(Modifier.width(8.dp))
                        DdSpinner(size = 16.dp, strokeWidth = 2.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    DdTextAction(
                        text = stringResource(R.string.mcp_delete),
                        onClick = { showDelete = true },
                        modifier = Modifier.testTag("mcp_delete"),
                        color = colors.danger,
                        enabled = !saving,
                    )
                }
                Spacer(Modifier.height(8.dp))
                DdPrimaryButton(
                    text = stringResource(R.string.mcp_save),
                    onClick = { save() },
                    modifier = Modifier.fillMaxWidth().testTag("mcp_save"),
                    enabled = !saving && loaded && !missing,
                )
            }
        }
    }

    if (showDelete) {
        DdDialog(
            onDismissRequest = { showDelete = false },
            modifier = Modifier.testTag("mcp_delete_dialog"),
            title = stringResource(R.string.mcp_delete_title),
            content = {
                Text(
                    text = stringResource(R.string.mcp_delete_message, target.name),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.mcp_cancel),
                    onClick = { showDelete = false },
                    modifier = Modifier.testTag("mcp_delete_cancel"),
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.mcp_delete_confirm),
                    onClick = { showDelete = false; delete() },
                    modifier = Modifier.testTag("mcp_delete_confirm"),
                    color = colors.danger,
                )
            },
        )
    }
}

@Composable
private fun TestResultLine(result: McpProbeResult) {
    val colors = deepDeepColors()
    val error = result.error
    val text = when {
        result.state == McpProbeState.OK -> stringResource(R.string.mcp_status_ok, result.toolCount)
        result.state == McpProbeState.AUTH_REQUIRED -> stringResource(R.string.mcp_status_auth)
        result.state == McpProbeState.UNSUPPORTED -> stringResource(R.string.mcp_status_unsupported)
        error.isNullOrBlank() -> stringResource(R.string.mcp_status_failed)
        else -> stringResource(R.string.mcp_test_failed, error)
    }
    val color = when (result.state) {
        McpProbeState.OK -> colors.accent
        McpProbeState.FAILED -> colors.danger
        else -> colors.inkTertiary
    }
    Text(
        text = text,
        modifier = Modifier.testTag("mcp_test_result"),
        style = DdType.bodySmall,
        color = color,
    )
}

@Composable
private fun OAuthSection(
    serverName: String,
    config: McpServerConfig.Http,
    oauth: McpOAuthService,
    onAuthenticated: () -> Unit,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val openUrl: (String) -> Unit = remember(context) {
        { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    }
    val session = remember(serverName, config.url) { McpOAuthSession(serverName, config.url, openUrl) }
    val ui by session.state.collectAsStateWithLifecycle()
    var signedIn by remember(serverName, config.url) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var callbackDraft by remember { mutableStateOf("") }

    LaunchedEffect(serverName, config.url) {
        signedIn = runCatching { oauth.hasToken(serverName, config) }.getOrDefault(false)
    }

    fun signIn() {
        if (busy) return
        busy = true
        failure = null
        callbackDraft = ""
        session.begin()
        scope.launch {
            var server: OAuthCallbackServer<String>? = null
            try {
                server = startMcpCallbackServer(serverName)
                session.attachCallback(server)
                val result = oauth.signIn(serverName, config, session, session.redirectUri)
                val error = result.exceptionOrNull()
                when {
                    result.isSuccess -> {
                        signedIn = true
                        onAuthenticated()
                    }
                    error is CancellationException -> Unit
                    else -> failure = error?.message
                }
            } finally {
                runCatching { server?.close() }
                busy = false
            }
        }
    }

    fun signOut() {
        if (busy) return
        failure = null
        scope.launch {
            runCatching { oauth.signOut(serverName, config) }
            signedIn = false
            onAuthenticated()
        }
    }

    fun submitCallback() {
        if (session.submitManualCallback(callbackDraft)) callbackDraft = ""
    }

    Column(Modifier.fillMaxWidth()) {
        DdSectionLabel(stringResource(R.string.mcp_oauth_label))
        Spacer(Modifier.height(8.dp))
        when {
            busy -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DdSpinner(size = 16.dp, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.mcp_oauth_waiting),
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
                if (ui.authorizationUrl != null) {
                    DdTextAction(
                        text = stringResource(R.string.mcp_oauth_open),
                        onClick = { session.reopen() },
                        modifier = Modifier.testTag("mcp_oauth_open"),
                    )
                }
                if (ui.manualPrompt) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.mcp_oauth_manual_hint),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    DdTextField(
                        value = callbackDraft,
                        onValueChange = { callbackDraft = it },
                        placeholder = stringResource(R.string.mcp_oauth_manual_placeholder),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        onDone = { submitCallback() },
                        testTag = "mcp_oauth_callback",
                    )
                    if (ui.error == McpOAuthError.INVALID_CALLBACK) {
                        Text(
                            text = stringResource(R.string.mcp_oauth_callback_invalid),
                            modifier = Modifier.padding(top = 8.dp),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    DdTextAction(
                        text = stringResource(R.string.mcp_oauth_manual_submit),
                        onClick = { submitCallback() },
                        modifier = Modifier.testTag("mcp_oauth_callback_submit"),
                        enabled = callbackDraft.isNotBlank(),
                    )
                }
                DdTextAction(
                    text = stringResource(R.string.mcp_oauth_cancel),
                    onClick = { session.cancel() },
                    modifier = Modifier.testTag("mcp_oauth_cancel"),
                    color = colors.inkTertiary,
                )
            }

            signedIn -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.mcp_oauth_signed_in),
                        modifier = Modifier.weight(1f),
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                    DdTextAction(
                        text = stringResource(R.string.mcp_oauth_logout),
                        onClick = { signOut() },
                        modifier = Modifier.testTag("mcp_oauth_logout"),
                        color = colors.inkSecondary,
                    )
                }
            }

            else -> {
                DdTextAction(
                    text = stringResource(R.string.mcp_oauth_login),
                    onClick = { signIn() },
                    modifier = Modifier.testTag("mcp_oauth_login"),
                )
            }
        }
        failure?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                text = stringResource(R.string.mcp_oauth_failed, detail),
                modifier = Modifier.padding(top = 8.dp),
                style = DdType.bodySmall,
                color = colors.danger,
            )
        }
    }
}
