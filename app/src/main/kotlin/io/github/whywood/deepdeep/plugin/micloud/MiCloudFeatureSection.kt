package io.github.whywood.deepdeep.plugin.micloud

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.settings.SettingsGroup
import kotlinx.coroutines.launch

@Composable
fun MiCloudFeatureSection(
    preferences: MiCloudSettings,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val capabilities by preferences.capabilities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxWidth().testTag("micloud_features")) {
        DdSectionLabel(
            stringResource(R.string.micloud_features_title),
            Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        SettingsGroup {
            if (!enabled) {
                Text(
                    text = stringResource(R.string.micloud_features_login_required),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                DdHairline()
            }
            FeatureRow(
                titleRes = R.string.micloud_feature_notes,
                checked = capabilities.notesRead && capabilities.notesWrite,
                enabled = enabled,
                testTag = "micloud_feature_notes",
                onCheckedChange = { value ->
                    scope.launch { preferences.setFeature(MiCloudFeature.NOTES, value) }
                },
            )
            DdHairline()
            FeatureRow(
                titleRes = R.string.micloud_feature_todos,
                checked = capabilities.todosRead && capabilities.todosWrite,
                enabled = enabled,
                testTag = "micloud_feature_todos",
                onCheckedChange = { value ->
                    scope.launch { preferences.setFeature(MiCloudFeature.TODOS, value) }
                },
            )
            DdHairline()
            FeatureRow(
                titleRes = R.string.micloud_feature_sms,
                checked = capabilities.smsRead,
                enabled = enabled,
                testTag = "micloud_feature_sms",
                onCheckedChange = { value ->
                    scope.launch { preferences.setRead(MiCloudFeature.SMS, value) }
                },
            )
            DdHairline()
            FeatureRow(
                titleRes = R.string.micloud_feature_contacts,
                checked = capabilities.contactsRead,
                enabled = enabled,
                testTag = "micloud_feature_contacts",
                onCheckedChange = { value ->
                    scope.launch { preferences.setRead(MiCloudFeature.CONTACTS, value) }
                },
            )
            DdHairline()
            FeatureRow(
                titleRes = R.string.micloud_feature_gallery,
                checked = capabilities.galleryRead,
                enabled = enabled,
                testTag = "micloud_feature_gallery",
                onCheckedChange = { value ->
                    scope.launch { preferences.setRead(MiCloudFeature.GALLERY, value) }
                },
            )
            DdHairline()
            FeatureRow(
                titleRes = R.string.micloud_feature_recordings,
                checked = capabilities.recordingsRead,
                enabled = enabled,
                testTag = "micloud_feature_recordings",
                onCheckedChange = { value ->
                    scope.launch { preferences.setRead(MiCloudFeature.RECORDINGS, value) }
                },
            )
            DdHairline()
            FeatureRow(
                titleRes = R.string.micloud_feature_account,
                checked = capabilities.accountRead,
                enabled = enabled,
                testTag = "micloud_feature_account",
                onCheckedChange = { value ->
                    scope.launch { preferences.setRead(MiCloudFeature.ACCOUNT, value) }
                },
            )
        }
    }
}

@Composable
private fun FeatureRow(
    @StringRes titleRes: Int,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    DdListRow(
        title = stringResource(titleRes),
        enabled = enabled,
        onClick = { if (enabled) onCheckedChange(!checked) },
        trailing = {
            DdSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                stateDescription = stringResource(
                    if (checked) R.string.settings_switch_on else R.string.settings_switch_off,
                ),
                modifier = Modifier.testTag(testTag),
            )
        },
    )
}
