package io.github.whywood.deepdeep.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.RuntimeProject

@Composable
fun ProjectPickerDialog(
    projects: List<RuntimeProject>,
    currentProjectId: String,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(vertical = 16.dp)
                .testTag("project_picker"),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.project_picker_title),
                    modifier = Modifier.weight(1f),
                    style = DdType.body,
                    color = colors.ink,
                )
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.assistant_close),
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                projects.forEach { project ->
                    ProjectRow(
                        project = project,
                        selected = project.id == currentProjectId,
                        onClick = { onSelect(project.id) },
                    )
                }
            }
            DdHairline()
            if (creating) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.wash)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("project_name_input"),
                        textStyle = DdType.body.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent)
                            .clickable(enabled = name.isNotBlank()) {
                                onCreate(name.trim())
                                creating = false
                                name = ""
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("project_create_confirm"),
                    ) {
                        Text(
                            text = stringResource(R.string.project_create_confirm),
                            style = DdType.bodySmall,
                            color = colors.onAccent,
                        )
                    }
                }
            } else {
                DdPressable(
                    onClick = { creating = true },
                    modifier = Modifier.fillMaxWidth().testTag("project_create"),
                    background = colors.surface,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DdIcon(icon = DdIcons.Plus, tint = colors.inkSecondary, size = 16.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.project_create),
                            style = DdType.body,
                            color = colors.ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: RuntimeProject,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("project_${project.id}"),
        background = colors.surface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = DdType.body,
                    color = colors.ink,
                )
                if (project.isHome) {
                    Text(
                        text = stringResource(R.string.project_home_subtitle),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }
            if (selected) {
                DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
            }
        }
    }
}
