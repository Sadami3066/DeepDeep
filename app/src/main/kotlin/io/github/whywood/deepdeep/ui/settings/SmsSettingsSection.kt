package io.github.whywood.deepdeep.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.tool.hasReadSmsPermission

@Composable
internal fun SmsSettingsSection(
    readSmsEnabled: Boolean,
    onReadSmsEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasReadSmsPermission(context)) }
    val deniedMessage = stringResource(R.string.sms_permission_denied)
    LifecycleResumeEffect(Unit) {
        granted = hasReadSmsPermission(context)
        onPauseOrDispose {}
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        if (result) {
            onReadSmsEnabledChange(true)
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)
            if (canAskAgain) {
                Toast.makeText(context, deniedMessage, Toast.LENGTH_SHORT).show()
            } else {
                openSmsAppSettings(context)
            }
        }
    }
    val changeEnabled: (Boolean) -> Unit = { value ->
        when {
            !value -> onReadSmsEnabledChange(false)
            hasReadSmsPermission(context) -> onReadSmsEnabledChange(true)
            else -> requestPermission.launch(Manifest.permission.READ_SMS)
        }
    }
    DdListRow(
        icon = DdIcons.Message,
        title = stringResource(R.string.sms_read_title),
        subtitle = stringResource(
            if (granted) R.string.sms_read_description else R.string.sms_permission_needed,
        ),
        subtitleMaxLines = 2,
        onClick = {
            if (granted) changeEnabled(!readSmsEnabled) else requestPermission.launch(Manifest.permission.READ_SMS)
        },
        showChevron = !granted,
        modifier = modifier.testTag("sms_read"),
        trailing = if (granted) {
            {
                DdSwitch(
                    checked = readSmsEnabled,
                    onCheckedChange = changeEnabled,
                    stateDescription = stringResource(
                        if (readSmsEnabled) R.string.settings_switch_on else R.string.settings_switch_off,
                    ),
                    modifier = Modifier.testTag("sms_read_switch"),
                )
            }
        } else {
            null
        },
    )
}

private fun openSmsAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
