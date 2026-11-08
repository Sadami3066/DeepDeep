package io.github.whywood.deepdeep.ui.mcp

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pi.ai.auth.oauth.OAuthCallbackServer
import dev.pi.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpOAuthService
import io.github.whywood.deepdeep.runtime.McpProbeResult
import io.github.whywood.deepdeep.runtime.McpProbeState
import io.github.whywood.deepdeep.runtime.McpServerProbe
import io.github.whywood.deepdeep.runtime.parseMcpEntries
import io.github.whywood.deepdeep.ui.common.chat.copyToClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EDITOR_JSON_MIN_HEIGHT = 220.dp
private val EDITOR_BUTTON_HEIGHT = 52.dp
private val EDITOR_GUTTER_WIDTH = 26.dp
private val EDITOR_MONO_SIZE = 13.sp
private val EDITOR_MONO_LINE = 20.sp

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(target.name) { mutableStateOf("") }
    var loaded by rememberSaveable(target.name) { mutableStateOf(false) }
    var missing by remember(target.name) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<McpProbeResult?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val notFoundMessage = stringResource(R.string.mcp_error_not_found)
    val jsonInvalidMessage = stringResource(R.string.mcp_error_json_invalid)
    val saveFailedMessage = stringResource(R.string.mcp_error_save_failed)
    val deleteFailedMessage = stringResource(R.string.mcp_error_delete_failed)

    LaunchedEffect(target.name) {
        if (!loaded && !missing) {
            val stored = store.entryJson(target.name)
            if (stored == null) {
                missing = true
                error = notFoundMessage
            } else {
                text = stored
                loaded = true
            }
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val parsed = if (loaded) {
        remember(text) { runCatching { parseMcpEntries(text).singleOrNull() }.getOrNull() }
    } else {
        null
    }
    val jsonErrorLine = if (loaded && parsed == null) {
        remember(text) { locateJsonErrorLine(text) }
    } else {
        null
    }
    val http = parsed?.config as? McpServerConfig.Http
    val hasAuthorization = http?.headers?.keys?.any { it.equals("Authorization", ignoreCase = true) } == true

    fun save() {
        if (saving || testing || !loaded || missing) return
        if (parsed == null) {
            error = jsonInvalidMessage
            return
        }
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
                onFailure = { failure -> error = failure.message ?: saveFailedMessage },
            )
        }
    }

    fun test() {
        val entry = parsed ?: run {
            error = jsonInvalidMessage
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

    fun format() {
        val pretty = prettyJson(text) ?: return
        text = pretty
        error = null
        testResult = null
    }

    fun copy() {
        if (copyToClipboard(context, text)) copied = true
    }

    fun delete() {
        if (saving || testing) return
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
                error = deleteFailedMessage
            }
        }
    }

    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).testTag("mcp_editor_sheet")) {
            EditorHeader(serverName = target.name, onClose = onDismiss)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DdSpacing.Xxl),
            ) {
                Spacer(Modifier.height(DdSpacing.Sm))
                if (missing) {
                    Text(
                        text = stringResource(R.string.mcp_error_load_failed),
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                } else if (!loaded) {
                    DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                } else {
                    ConfigHeader(
                        enabled = parsed != null && !saving,
                        onFormat = { format() },
                    )
                    Spacer(Modifier.height(DdSpacing.Sm))
                    JsonEditorCard(
                        text = text,
                        onTextChange = {
                            text = it
                            error = null
                            testResult = null
                            copied = false
                        },
                        valid = parsed != null,
                        errorLine = jsonErrorLine,
                        copied = copied,
                        onCopy = { copy() },
                    )
                    error?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier.padding(top = DdSpacing.Md),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                    testResult?.let { result ->
                        Spacer(Modifier.height(DdSpacing.Md))
                        TestResultLine(result)
                    }
                    if (http != null && !hasAuthorization) {
                        Spacer(Modifier.height(DdSpacing.Xxl))
                        AuthSection(
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
                    Spacer(Modifier.height(DdSpacing.Xl))
                    Row(Modifier.fillMaxWidth()) {
                        DdTextAction(
                            text = stringResource(R.string.mcp_delete),
                            onClick = { showDelete = true },
                            modifier = Modifier.testTag("mcp_delete"),
                            color = colors.danger,
                            enabled = !saving && !testing,
                        )
                    }
                }
                Spacer(Modifier.height(DdSpacing.Xxl))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DdSpacing.Xxl, vertical = DdSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(DdSpacing.Md),
            ) {
                EditorButton(
                    text = stringResource(if (testing) R.string.mcp_testing else R.string.mcp_test),
                    onClick = { test() },
                    modifier = Modifier.weight(1f).testTag("mcp_test"),
                    enabled = !testing && !saving && loaded && !missing,
                    loading = testing,
                    primary = false,
                )
                EditorButton(
                    text = stringResource(if (saving) R.string.mcp_saving else R.string.mcp_save_changes),
                    onClick = { save() },
                    modifier = Modifier.weight(1f).testTag("mcp_save"),
                    enabled = !saving && !testing && loaded && !missing,
                    loading = saving,
                    primary = true,
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
private fun EditorHeader(serverName: String, onClose: () -> Unit) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(start = DdSpacing.Xxl, end = DdSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIcon(icon = DdIcons.Plug, tint = colors.inkSecondary, size = 22.dp)
        Spacer(Modifier.width(DdSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.mcp_editor_title),
                style = DdType.heading,
                color = colors.ink,
            )
            if (serverName.isNotBlank()) {
                Text(
                    text = serverName,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DdIconButton(
            icon = DdIcons.Close,
            onClick = onClose,
            contentDescription = stringResource(R.string.mcp_close),
            tint = colors.inkSecondary,
            iconSize = 20.dp,
        )
    }
}

@Composable
private fun ConfigHeader(enabled: Boolean, onFormat: () -> Unit) {
    val colors = deepDeepColors()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.mcp_editor_config_label),
            style = DdType.label,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .clip(DdShapes.Small)
                .background(colors.wash)
                .padding(horizontal = DdSpacing.Sm, vertical = DdSpacing.Xxs),
        ) {
            Text(
                text = stringResource(R.string.mcp_json_badge),
                style = DdType.chip,
                color = colors.inkTertiary,
            )
        }
        DdTextAction(
            text = stringResource(R.string.mcp_format),
            onClick = onFormat,
            modifier = Modifier.testTag("mcp_format"),
            enabled = enabled,
        )
    }
}

@Composable
private fun JsonEditorCard(
    text: String,
    onTextChange: (String) -> Unit,
    valid: Boolean,
    errorLine: Int?,
    copied: Boolean,
    onCopy: () -> Unit,
) {
    val colors = deepDeepColors()
    val horizontal = rememberScrollState()
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = EDITOR_MONO_SIZE,
        lineHeight = EDITOR_MONO_LINE,
        color = colors.ink,
    )
    val gutterStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = EDITOR_MONO_LINE,
        color = colors.inkTertiary,
    )
    val lineCount = remember(text) { if (text.isEmpty()) 1 else text.count { it == '\n' } + 1 }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(DdShapes.Segmented)
            .background(colors.codeBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = EDITOR_JSON_MIN_HEIGHT)
                .padding(top = DdSpacing.Md, bottom = DdSpacing.Md),
        ) {
            Column(
                modifier = Modifier
                    .width(EDITOR_GUTTER_WIDTH)
                    .padding(start = DdSpacing.Sm),
                horizontalAlignment = Alignment.End,
            ) {
                for (line in 1..lineCount) {
                    Text(
                        text = line.toString(),
                        style = gutterStyle,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Spacer(Modifier.width(DdSpacing.Sm))
            Box(
                Modifier
                    .weight(1f)
                    .horizontalScroll(horizontal),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .heightIn(min = EDITOR_JSON_MIN_HEIGHT)
                        .testTag("mcp_config_json"),
                    textStyle = monoStyle,
                    cursorBrush = SolidColor(colors.accent),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Default,
                    ),
                    visualTransformation = JsonHighlight(colors.accent, colors.codeString, colors.codeNumber, colors.codeKeyword, colors.inkSecondary),
                )
            }
        }
        DdHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DdSpacing.Md, end = DdSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(
                icon = if (valid) DdIcons.Check else DdIcons.Info,
                tint = if (valid) colors.success else colors.danger,
                size = 14.dp,
            )
            Spacer(Modifier.width(DdSpacing.Xs))
            Text(
                text = when {
                    valid -> stringResource(R.string.mcp_json_valid)
                    errorLine != null -> stringResource(R.string.mcp_json_invalid_line, errorLine)
                    else -> stringResource(R.string.mcp_json_invalid)
                },
                style = DdType.bodySmall,
                color = if (valid) colors.inkSecondary else colors.danger,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            DdTextAction(
                text = stringResource(if (copied) R.string.mcp_copied else R.string.mcp_copy),
                onClick = onCopy,
                modifier = Modifier.testTag("mcp_copy"),
                enabled = text.isNotBlank(),
            )
        }
    }
}

@Composable
private fun EditorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    loading: Boolean,
    primary: Boolean,
) {
    val colors = deepDeepColors()
    val active = enabled && !loading
    DdPressable(
        onClick = onClick,
        modifier = modifier.height(EDITOR_BUTTON_HEIGHT),
        shape = DdShapes.Large,
        background = if (primary && active) colors.accent else colors.wash,
        enabled = active,
        contentDescription = text,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DdSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                DdSpinner(
                    size = 15.dp,
                    strokeWidth = 2.dp,
                    color = if (primary) colors.onAccent else colors.inkTertiary,
                )
                Spacer(Modifier.width(DdSpacing.Sm))
            }
            Text(
                text = text,
                style = DdType.button,
                color = when {
                    primary && active -> colors.onAccent
                    primary -> colors.inkTertiary
                    active -> colors.ink
                    else -> colors.inkTertiary
                },
            )
        }
    }
}

@Composable
private fun AuthSection(
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

    DdGroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = DdSpacing.Card, vertical = DdSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(icon = DdIcons.Shield, tint = colors.inkSecondary, size = 20.dp)
            Spacer(Modifier.width(DdSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mcp_editor_auth_label),
                    style = DdType.body,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(
                        if (signedIn) R.string.mcp_oauth_signed_in else R.string.mcp_oauth_not_signed_in,
                    ),
                    style = DdType.bodySmall,
                    color = if (signedIn) colors.success else colors.inkTertiary,
                )
            }
            when {
                busy -> DdTextAction(
                    text = stringResource(R.string.mcp_oauth_cancel),
                    onClick = { session.cancel() },
                    modifier = Modifier.testTag("mcp_oauth_cancel"),
                    color = colors.inkTertiary,
                )
                signedIn -> DdTextAction(
                    text = stringResource(R.string.mcp_oauth_logout),
                    onClick = { signOut() },
                    modifier = Modifier.testTag("mcp_oauth_logout"),
                    color = colors.inkSecondary,
                )
                else -> DdTextAction(
                    text = stringResource(R.string.mcp_oauth_login_action),
                    onClick = { signIn() },
                    modifier = Modifier.testTag("mcp_oauth_login"),
                )
            }
        }
        if (busy) {
            Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Card).padding(bottom = DdSpacing.Md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DdSpinner(size = 16.dp, strokeWidth = 2.dp)
                    Spacer(Modifier.width(DdSpacing.Sm))
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
                    Spacer(Modifier.height(DdSpacing.Sm))
                    Text(
                        text = stringResource(R.string.mcp_oauth_manual_hint),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(DdSpacing.Sm))
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
                            modifier = Modifier.padding(top = DdSpacing.Sm),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                    Spacer(Modifier.height(DdSpacing.Xs))
                    DdTextAction(
                        text = stringResource(R.string.mcp_oauth_manual_submit),
                        onClick = { submitCallback() },
                        modifier = Modifier.testTag("mcp_oauth_callback_submit"),
                        enabled = callbackDraft.isNotBlank(),
                    )
                }
            }
        }
        failure?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                text = stringResource(R.string.mcp_oauth_failed, detail),
                modifier = Modifier.padding(horizontal = DdSpacing.Card).padding(bottom = DdSpacing.Md),
                style = DdType.bodySmall,
                color = colors.danger,
            )
        }
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

private class JsonHighlight(
    private val keyColor: Color,
    private val stringColor: Color,
    private val numberColor: Color,
    private val literalColor: Color,
    private val punctuationColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val highlighted = buildAnnotatedString {
            append(source)
            var index = 0
            while (index < source.length) {
                val char = source[index]
                when {
                    char == '"' -> {
                        val start = index
                        index++
                        var escaped = false
                        while (index < source.length) {
                            val current = source[index]
                            if (escaped) {
                                escaped = false
                            } else if (current == '\\') {
                                escaped = true
                            } else if (current == '"') {
                                index++
                                break
                            }
                            index++
                        }
                        var probe = index
                        while (probe < source.length && source[probe].isWhitespace()) probe++
                        val color = if (probe < source.length && source[probe] == ':') keyColor else stringColor
                        addStyle(SpanStyle(color = color), start, index)
                    }
                    char == '-' || char.isDigit() -> {
                        val start = index
                        index++
                        while (index < source.length && (source[index].isDigit() || source[index] in ".eE+-")) index++
                        addStyle(SpanStyle(color = numberColor), start, index)
                    }
                    source.startsWith("true", index) || source.startsWith("false", index) || source.startsWith("null", index) -> {
                        val length = when {
                            source.startsWith("true", index) || source.startsWith("null", index) -> 4
                            else -> 5
                        }
                        addStyle(SpanStyle(color = literalColor), index, index + length)
                        index += length
                    }
                    char == '{' || char == '}' || char == '[' || char == ']' || char == ':' || char == ',' -> {
                        addStyle(SpanStyle(color = punctuationColor), index, index + 1)
                        index++
                    }
                    else -> index++
                }
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

private fun prettyJson(text: String): String? {
    val out = StringBuilder()
    var depth = 0
    var inString = false
    var escaped = false
    var pendingBreak = false
    fun flushBreak() {
        if (pendingBreak) {
            pendingBreak = false
            out.append('\n')
            repeat(depth) { out.append("  ") }
        }
    }
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (inString) {
            out.append(char)
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            index++
            continue
        }
        when {
            char == '"' -> {
                flushBreak()
                inString = true
                out.append(char)
            }
            char == '{' || char == '[' -> {
                flushBreak()
                out.append(char)
                depth++
                pendingBreak = true
            }
            char == '}' || char == ']' -> {
                if (pendingBreak) {
                    pendingBreak = false
                    depth--
                } else {
                    depth--
                    flushBreak()
                }
                out.append(char)
            }
            char == ',' -> {
                out.append(char)
                pendingBreak = true
            }
            char == ':' -> out.append(": ")
            char.isWhitespace() -> Unit
            else -> {
                flushBreak()
                out.append(char)
            }
        }
        index++
    }
    if (inString) return null
    return out.toString().trim()
}

private fun locateJsonErrorLine(text: String): Int? {
    var line = 1
    var inString = false
    var escaped = false
    val open = ArrayDeque<Pair<Char, Int>>()
    var index = 0
    while (index < text.length) {
        val char = text[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        } else {
            when (char) {
                '"' -> inString = true
                '{', '[' -> open.addLast(char to line)
                '}', ']' -> {
                    val opener = open.removeLastOrNull() ?: return line
                    if ((opener.first == '{') != (char == '}')) return line
                }
            }
        }
        if (char == '\n') line++
        index++
    }
    if (inString) return line
    return open.lastOrNull()?.second
}
