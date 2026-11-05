package io.github.whywood.deepdeep.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ProviderAuthKind
import io.github.whywood.deepdeep.runtime.ProviderOAuthService
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.ui.common.OAuthLoginSession
import io.github.whywood.deepdeep.ui.common.OAuthLoginSheet
import io.github.whywood.deepdeep.ui.common.ProviderIcon
import kotlinx.coroutines.launch

@Composable
fun ProviderDetailScreen(
    provider: ProviderSummary,
    configured: Boolean,
    savedApiKey: String? = null,
    authKind: ProviderAuthKind? = null,
    oauthService: ProviderOAuthService? = null,
    onAuthChanged: () -> Unit = {},
    onBack: () -> Unit,
    onSaveApiKey: suspend (String) -> Result<Unit>,
    onClearApiKey: suspend () -> Result<Unit>,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val oauthLoginFailed = stringResource(R.string.settings_oauth_login_failed)
    val oauthSignedIn = authKind == ProviderAuthKind.OAUTH
    var oauthSession by remember(provider.id) { mutableStateOf<OAuthLoginSession?>(null) }

    fun beginOAuthLogin() {
        val service = oauthService ?: return
        if (oauthSession != null) return
        val session = OAuthLoginSession(
            providerName = provider.name,
            onOpenUrl = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
        )
        oauthSession = session
        scope.launch {
            val result = service.login(provider.id, session)
            if (result.isSuccess) {
                session.succeed()
                onAuthChanged()
                if (oauthSession === session) oauthSession = null
            } else {
                val detail = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                session.fail(if (detail == null) oauthLoginFailed else "$oauthLoginFailed: $detail")
            }
        }
    }

    fun signOutOAuth() {
        val service = oauthService ?: return
        scope.launch {
            runCatching { service.logout(provider.id) }
            onAuthChanged()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
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
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_provider_title),
                style = DdType.pageTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            ProviderOverview(provider = provider, configured = configured)
            Spacer(Modifier.height(28.dp))
            SettingsGroup {
                val showOAuth = provider.hasOAuth && oauthService != null
                if (!provider.supportsApiKey && !showOAuth) {
                    DdListRow(
                        icon = DdIcons.Info,
                        title = stringResource(R.string.settings_provider_extra_config),
                        subtitle = stringResource(R.string.settings_provider_extra_config_body),
                    )
                }
                if (showOAuth) {
                    OAuthSection(
                        signedIn = oauthSignedIn,
                        onLogin = { beginOAuthLogin() },
                        onLogout = { signOutOAuth() },
                    )
                }
                if (provider.supportsApiKey) {
                    if (showOAuth) DdHairline()
                    ApiKeySection(
                        configured = savedApiKey != null,
                        savedApiKey = savedApiKey,
                        oauthSignedIn = oauthSignedIn,
                        onSave = onSaveApiKey,
                        onClear = onClearApiKey,
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    oauthSession?.let { session ->
        OAuthLoginSheet(
            providerName = provider.name,
            session = session,
            onDismiss = {
                session.cancel()
                if (oauthSession === session) oauthSession = null
            },
        )
    }
}

@Composable
private fun OAuthSection(
    signedIn: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = deepDeepColors()
    DdListRow(
        icon = DdIcons.Shield,
        title = stringResource(R.string.settings_oauth_method),
        value = if (signedIn) {
            "${stringResource(R.string.settings_oauth)} · ${stringResource(R.string.settings_oauth_signed_in)}"
        } else {
            stringResource(R.string.settings_oauth)
        },
        onClick = if (signedIn) null else onLogin,
        showChevron = !signedIn,
    )
    if (signedIn) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            DdTextAction(
                text = stringResource(R.string.settings_oauth_sign_out),
                onClick = onLogout,
                color = colors.inkTertiary,
            )
        }
    }
}

@Composable
private fun ProviderOverview(
    provider: ProviderSummary,
    configured: Boolean,
) {
    val colors = deepDeepColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(provider.id, provider.name, size = 52.dp, iconSize = 30.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.settings_provider_models_count, provider.modelCount),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
            when {
                configured -> StatusPill(stringResource(R.string.settings_status_configured), active = false)
                else -> StatusPill(stringResource(R.string.settings_status_unconfigured), active = false)
            }
        }
        DdHairline()
        DdListRow(
            title = stringResource(R.string.settings_provider_model_count),
            value = provider.modelCount.toString(),
        )
        DdHairline()
        DdListRow(
            title = stringResource(R.string.settings_provider_base_url),
            value = provider.baseUrl?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.settings_provider_default_base_url),
        )
    }
}

private enum class ApiKeyOutcome { SAVED, SAVE_FAILED, CLEAR_FAILED }

@Composable
private fun ApiKeySection(
    configured: Boolean,
    savedApiKey: String?,
    oauthSignedIn: Boolean,
    onSave: suspend (String) -> Result<Unit>,
    onClear: suspend () -> Result<Unit>,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var showRequired by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ApiKeyOutcome?>(null) }

    fun submit() {
        if (saving) return
        val key = draft.trim()
        if (key.isEmpty()) {
            showRequired = !configured
            return
        }
        showRequired = false
        outcome = null
        saving = true
        scope.launch {
            val result = onSave(key)
            saving = false
            if (result.isSuccess) {
                draft = ""
                outcome = ApiKeyOutcome.SAVED
            } else {
                outcome = ApiKeyOutcome.SAVE_FAILED
            }
        }
    }

    fun clear() {
        if (saving) return
        showRequired = false
        outcome = null
        saving = true
        scope.launch {
            val result = onClear()
            saving = false
            if (result.isSuccess) {
                draft = ""
            } else {
                outcome = ApiKeyOutcome.CLEAR_FAILED
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (oauthSignedIn) {
            Text(
                text = stringResource(R.string.settings_oauth_api_key_notice),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = stringResource(R.string.settings_provider_api_key),
            style = DdType.micro,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(8.dp))
        DdTextField(
            value = draft,
            onValueChange = {
                draft = it
                showRequired = false
                outcome = null
            },
            placeholder = if (configured) {
                maskApiKey(savedApiKey)
            } else {
                stringResource(R.string.settings_provider_api_key_hint)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = PasswordVisualTransformation(),
            onDone = { submit() },
        )
        if (showRequired) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_provider_api_key_required),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        }
        outcome?.let { current ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    when (current) {
                        ApiKeyOutcome.SAVED -> R.string.settings_provider_api_key_saved
                        ApiKeyOutcome.SAVE_FAILED -> R.string.settings_provider_api_key_save_failed
                        ApiKeyOutcome.CLEAR_FAILED -> R.string.settings_provider_api_key_clear_failed
                    },
                ),
                modifier = Modifier.testTag("provider_api_key_feedback"),
                style = DdType.bodySmall,
                color = if (current == ApiKeyOutcome.SAVED) colors.accent else colors.danger,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DdTextAction(
                text = stringResource(R.string.settings_provider_api_key_save),
                onClick = { submit() },
                enabled = draft.isNotBlank() && !saving,
            )
            Spacer(Modifier.weight(1f))
            if (configured) {
                DdTextAction(
                    text = stringResource(R.string.settings_provider_api_key_clear),
                    onClick = { clear() },
                    color = colors.inkTertiary,
                    enabled = !saving,
                )
            }
        }
    }
}

internal fun maskApiKey(key: String?): String {
    val trimmed = key?.trim().orEmpty()
    return if (trimmed.length >= 6) trimmed.take(3) + "*****" else "*****"
}
