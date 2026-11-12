package io.github.whywood.deepdeep.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.update.UpdateFailure
import io.github.whywood.deepdeep.data.update.UpdatePhase
import io.github.whywood.deepdeep.data.update.UpdatePolicy
import io.github.whywood.deepdeep.data.update.UpdateUi
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.chat.ChatMarkdown
import io.github.whywood.deepdeep.ui.common.rememberRelativeTime

@Composable
fun SoftwareUpdateScreen(
    ui: UpdateUi,
    currentVersion: String,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val uriHandler = LocalUriHandler.current
    LifecycleResumeEffect(Unit) {
        onResume()
        onPauseOrDispose {}
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
        modifier = modifier.testTag("software_update"),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DdSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(R.string.settings_update),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Sm))
            Text(
                text = stringResource(R.string.update_current_version, currentVersion),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            when (val phase = ui.phase) {
                UpdatePhase.Idle, UpdatePhase.Checking -> CheckingState()

                UpdatePhase.UpToDate -> UpToDateState(
                    lastCheckedAt = ui.lastCheckedAt,
                    onCheck = onCheck,
                )

                is UpdatePhase.Available -> ReleaseNotes(notes = phase.release.notes)

                is UpdatePhase.Downloading -> DownloadingState(progress = phase.progress)

                is UpdatePhase.ReadyToInstall -> if (!phase.needsPermission) {
                    Text(
                        text = stringResource(R.string.update_ready),
                        style = DdType.body,
                        color = colors.inkSecondary,
                    )
                }

                is UpdatePhase.Failed -> FailedState(
                    failure = phase.failure,
                    onOpenBrowser = { uriHandler.openUri(UpdatePolicy.RELEASE_PAGE_URL) },
                )
            }
            Spacer(Modifier.height(DdSpacing.Huge))
        }
        when (val phase = ui.phase) {
            is UpdatePhase.Available -> BottomAction(
                caption = stringResource(
                    R.string.update_release_caption,
                    phase.release.versionName,
                    sizeLabel(phase.release.apkSize),
                ),
            ) {
                DdPrimaryButton(
                    text = stringResource(R.string.update_download_install),
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is UpdatePhase.ReadyToInstall -> BottomAction(
                caption = if (phase.needsPermission) {
                    stringResource(R.string.update_permission_needed)
                } else {
                    null
                },
            ) {
                if (phase.needsPermission) {
                    DdPrimaryButton(
                        text = stringResource(R.string.update_open_permission),
                        onClick = onOpenInstallPermission,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DdPrimaryButton(
                        text = stringResource(R.string.update_install),
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is UpdatePhase.Failed -> BottomAction {
                DdPrimaryButton(
                    text = stringResource(R.string.update_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun CheckingState() {
    val colors = deepDeepColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DdSpinner()
        Text(
            text = stringResource(R.string.update_checking),
            style = DdType.body,
            color = colors.inkSecondary,
        )
    }
}

@Composable
private fun UpToDateState(lastCheckedAt: Long?, onCheck: () -> Unit) {
    val colors = deepDeepColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DdIcon(icon = DdIcons.Check, tint = colors.success, size = 18.dp)
            Text(
                text = stringResource(R.string.update_up_to_date),
                style = DdType.body,
                color = colors.ink,
            )
        }
        DdTextAction(
            text = stringResource(R.string.update_recheck),
            onClick = onCheck,
        )
    }
    if (lastCheckedAt != null) {
        Text(
            text = stringResource(R.string.update_last_checked, rememberRelativeTime(lastCheckedAt)),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isBlank()) return
    ChatMarkdown(text = notes)
}

@Composable
private fun DownloadingState(progress: Float) {
    val colors = deepDeepColors()
    Text(
        text = stringResource(R.string.update_downloading, (progress * 100).toInt().coerceIn(0, 100)),
        style = DdType.body,
        color = colors.inkSecondary,
    )
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.wash),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
    }
}

@Composable
private fun FailedState(failure: UpdateFailure, onOpenBrowser: () -> Unit) {
    val colors = deepDeepColors()
    Text(
        text = stringResource(failureMessage(failure)),
        style = DdType.body,
        color = colors.inkSecondary,
    )
    if (failure == UpdateFailure.NETWORK || failure == UpdateFailure.NO_RELEASE) {
        Spacer(Modifier.height(DdSpacing.Sm))
        DdTextAction(
            text = stringResource(R.string.update_open_browser),
            onClick = onOpenBrowser,
        )
    }
}

@Composable
private fun BottomAction(caption: String? = null, button: @Composable () -> Unit) {
    val colors = deepDeepColors()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DdSpacing.ScreenHorizontal)
            .padding(top = DdSpacing.Sm, bottom = DdSpacing.Lg),
    ) {
        if (caption != null) {
            Text(
                text = caption,
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(10.dp))
        }
        button()
    }
}

@Composable
private fun sizeLabel(bytes: Long): String =
    stringResource(R.string.update_size_mb, (bytes / (1024L * 1024L)).toInt())

@StringRes
private fun failureMessage(failure: UpdateFailure): Int = when (failure) {
    UpdateFailure.NETWORK -> R.string.update_error_network
    UpdateFailure.SERVICE -> R.string.update_error_service
    UpdateFailure.NO_RELEASE -> R.string.update_error_no_release
    UpdateFailure.DOWNLOAD -> R.string.update_error_download
    UpdateFailure.VERIFY -> R.string.update_error_verify
    UpdateFailure.ARCHIVE -> R.string.update_error_archive
    UpdateFailure.INSTALL -> R.string.update_error_install
}
