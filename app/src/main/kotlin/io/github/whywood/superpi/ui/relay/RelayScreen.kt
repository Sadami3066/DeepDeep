package io.github.whywood.superpi.ui.relay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.whywood.superpi.R
import io.github.whywood.superpi.data.relay.RelayAccount
import io.github.whywood.superpi.data.relay.RelayDevice
import io.github.whywood.superpi.data.relay.RelayError
import io.github.whywood.superpi.data.relay.RelayLiveState
import io.github.whywood.superpi.data.relay.RelayPairingLink
import io.github.whywood.superpi.data.relay.RelayUiState
import io.github.whywood.superpi.design.DdDialog
import io.github.whywood.superpi.design.DdGroupCard
import io.github.whywood.superpi.design.DdIconButton
import io.github.whywood.superpi.design.DdIcons
import io.github.whywood.superpi.design.DdListRow
import io.github.whywood.superpi.design.DdNoticeBar
import io.github.whywood.superpi.design.DdNoticeTone
import io.github.whywood.superpi.design.DdPageScaffold
import io.github.whywood.superpi.design.DdPrimaryButton
import io.github.whywood.superpi.design.DdSpacing
import io.github.whywood.superpi.design.DdSwitch
import io.github.whywood.superpi.design.DdText
import io.github.whywood.superpi.design.DdTextAction
import io.github.whywood.superpi.design.DdTextField
import io.github.whywood.superpi.design.DdType
import io.github.whywood.superpi.design.superPiColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun RelayScreen(
    state: RelayUiState,
    liveState: RelayLiveState,
    allowRemote: Boolean,
    devices: List<RelayDevice>,
    defaultDeviceName: String,
    onBack: () -> Unit,
    onConnect: (link: String, loginSecret: String?, deviceName: String) -> Unit,
    onSignOut: () -> Unit,
    onDismissError: () -> Unit,
    onToggleAllowRemote: (Boolean) -> Unit,
) {
    val colors = superPiColors()
    var link by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf(defaultDeviceName) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val fingerprint = remember(link) { RelayPairingLink.parse(link)?.fingerprint }
    val connect = { onConnect(link.trim(), secret.trim().ifEmpty { null }, deviceName.trim()) }

    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.relay_back),
                iconSize = 21.dp,
                tint = colors.ink,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.relay_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
        },
        modifier = Modifier.testTag("relay_screen"),
    ) {
        val account = state.account
        if (account == null) {
            ConnectForm(
                link = link,
                onLinkChange = { link = it },
                secret = secret,
                onSecretChange = { secret = it },
                deviceName = deviceName,
                onDeviceNameChange = { deviceName = it },
                fingerprint = fingerprint,
                signingIn = state.signingIn,
                error = state.error,
                onConnect = connect,
                onDismissError = onDismissError,
            )
        } else {
            ConnectedAccount(
                account = account,
                liveState = liveState,
                allowRemote = allowRemote,
                devices = devices,
                onToggleAllowRemote = onToggleAllowRemote,
                onSignOut = { confirmSignOut = true },
            )
        }
    }

    if (confirmSignOut) {
        DdDialog(
            onDismissRequest = { confirmSignOut = false },
            title = stringResource(R.string.relay_sign_out_title),
            content = {
                Text(
                    text = stringResource(R.string.relay_sign_out_message),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.relay_cancel),
                    onClick = { confirmSignOut = false },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.relay_sign_out_confirm),
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    },
                    color = colors.danger,
                )
            },
        )
    }
}

@Composable
private fun ColumnScope.ConnectForm(
    link: String,
    onLinkChange: (String) -> Unit,
    secret: String,
    onSecretChange: (String) -> Unit,
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    fingerprint: String?,
    signingIn: Boolean,
    error: RelayError?,
    onConnect: () -> Unit,
    onDismissError: () -> Unit,
) {
    val colors = superPiColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DdSpacing.ScreenHorizontal),
    ) {
        Text(
            text = stringResource(R.string.relay_intro),
            style = DdType.bodySmall,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.height(DdSpacing.Xxl))
        FieldLabel(stringResource(R.string.relay_link_label))
        DdTextField(
            value = link,
            onValueChange = onLinkChange,
            placeholder = stringResource(R.string.relay_link_placeholder),
            singleLine = false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            testTag = "relay_link",
        )
        if (fingerprint != null) {
            Spacer(Modifier.height(DdSpacing.Sm))
            Text(
                text = stringResource(R.string.relay_fingerprint_label) + " · " + formatFingerprint(fingerprint),
                style = DdType.numeric,
                color = colors.inkTertiary,
            )
        }
        Spacer(Modifier.height(DdSpacing.Lg))
        FieldLabel(stringResource(R.string.relay_secret_label))
        DdTextField(
            value = secret,
            onValueChange = onSecretChange,
            placeholder = stringResource(R.string.relay_secret_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            visualTransformation = PasswordVisualTransformation(),
            testTag = "relay_secret",
        )
        Spacer(Modifier.height(DdSpacing.Lg))
        FieldLabel(stringResource(R.string.relay_device_name_label))
        DdTextField(
            value = deviceName,
            onValueChange = onDeviceNameChange,
            placeholder = stringResource(R.string.relay_device_name_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onDone = { if (link.isNotBlank() && !signingIn) onConnect() },
            testTag = "relay_device_name",
        )
        if (error != null) {
            Spacer(Modifier.height(DdSpacing.Xl))
            DdNoticeBar(
                text = stringResource(error.textRes()),
                tone = DdNoticeTone.DANGER,
                actionLabel = if (error.retryable()) stringResource(R.string.relay_retry) else null,
                onAction = if (error.retryable()) {
                    {
                        onDismissError()
                        onConnect()
                    }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(DdSpacing.Xl))
        DdPrimaryButton(
            text = stringResource(if (signingIn) R.string.relay_connecting else R.string.relay_connect),
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !signingIn && link.isNotBlank(),
        )
        Spacer(Modifier.height(DdSpacing.Xxxl))
    }
}

@Composable
private fun ColumnScope.ConnectedAccount(
    account: RelayAccount,
    liveState: RelayLiveState,
    allowRemote: Boolean,
    devices: List<RelayDevice>,
    onToggleAllowRemote: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = superPiColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DdSpacing.ScreenHorizontal),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.relay_connected),
                style = DdType.micro,
                color = colors.accent,
            )
        }
        Spacer(Modifier.height(DdSpacing.Md))
        DdGroupCard {
            DdListRow(
                title = stringResource(R.string.relay_account_device),
                value = account.deviceName,
            )
            DdListRow(
                title = stringResource(R.string.relay_account_server),
                value = account.origin,
            )
            DdListRow(
                title = stringResource(R.string.relay_account_since),
                value = formatDate(account.enrolledAt),
            )
        }
        Spacer(Modifier.height(DdSpacing.Xl))
        ShareCard(
            liveState = liveState,
            allowRemote = allowRemote,
            devices = devices,
            onToggleAllowRemote = onToggleAllowRemote,
        )
        Spacer(Modifier.height(DdSpacing.Xxxl))
        DdTextAction(
            text = stringResource(R.string.relay_sign_out),
            onClick = onSignOut,
            color = colors.danger,
        )
    }
}

@Composable
private fun ShareCard(
    liveState: RelayLiveState,
    allowRemote: Boolean,
    devices: List<RelayDevice>,
    onToggleAllowRemote: (Boolean) -> Unit,
) {
    val colors = superPiColors()
    DdGroupCard(
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.relay_share_title),
                        style = DdType.label,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(DdSpacing.Xxs))
                    Text(
                        text = stringResource(
                            if (allowRemote) liveState.textRes() else R.string.relay_share_off,
                        ),
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
                Spacer(Modifier.width(DdSpacing.Md))
                DdSwitch(
                    checked = allowRemote,
                    onCheckedChange = onToggleAllowRemote,
                    stateDescription = stringResource(
                        if (allowRemote) R.string.relay_share_on else R.string.relay_share_off,
                    ),
                )
            }
        },
    ) {
        if (devices.isEmpty()) {
            DdListRow(
                title = stringResource(R.string.relay_devices_empty),
                subtitle = stringResource(R.string.relay_devices_empty_hint),
            )
        } else {
            devices.forEach { device ->
                DdListRow(
                    title = device.name,
                    subtitle = if (device.online) {
                        device.bots.joinToString("、") { it.name }.ifEmpty {
                            stringResource(R.string.relay_devices_no_bots)
                        }
                    } else {
                        stringResource(R.string.relay_devices_offline)
                    },
                    trailing = {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (device.online) colors.accent else colors.hairline),
                        )
                    },
                )
            }
        }
    }
}

private fun RelayLiveState.textRes(): Int = when (this) {
    RelayLiveState.Disabled -> R.string.relay_share_off
    RelayLiveState.Connecting -> R.string.relay_live_connecting
    RelayLiveState.Online -> R.string.relay_live_online
    RelayLiveState.Offline -> R.string.relay_live_offline
    RelayLiveState.Unauthorized -> R.string.relay_live_unauthorized
}

@Composable
private fun FieldLabel(text: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = DdType.label,
            color = superPiColors().inkSecondary,
        )
        Spacer(Modifier.height(DdSpacing.Sm))
    }
}

private fun RelayError.textRes(): Int = when (this) {
    RelayError.InvalidLink -> R.string.relay_error_invalid_link
    RelayError.MissingCode -> R.string.relay_error_missing_code
    RelayError.MissingFingerprint -> R.string.relay_error_missing_fingerprint
    RelayError.FingerprintMismatch -> R.string.relay_error_fingerprint
    RelayError.Unreachable -> R.string.relay_error_unreachable
    RelayError.Unauthorized -> R.string.relay_error_unauthorized
    RelayError.BadResponse -> R.string.relay_error_bad_response
}

private fun RelayError.retryable(): Boolean = this == RelayError.Unreachable

private fun formatFingerprint(value: String): String =
    value.uppercase().chunked(4).joinToString(" ")

private fun formatDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
}
