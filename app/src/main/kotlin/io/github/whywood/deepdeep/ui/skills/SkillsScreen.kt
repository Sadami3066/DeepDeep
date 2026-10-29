package io.github.whywood.deepdeep.ui.skills

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.InstalledSkill
import io.github.whywood.deepdeep.data.SkillArchiveError
import io.github.whywood.deepdeep.data.SkillArchiveResult
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024

private data class SkillError(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
)

private sealed interface ArchiveRead {
    data class Success(val bytes: ByteArray, val fallbackName: String) : ArchiveRead
    data class Failure(@StringRes val messageRes: Int) : ArchiveRead
}

@Composable
fun SkillsScreen(
    repository: SkillRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val installSuccessTemplate = stringResource(R.string.skills_install_success)
    val scope = rememberCoroutineScope()
    var skills by remember { mutableStateOf<List<InstalledSkill>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var installing by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<InstalledSkill?>(null) }
    var deleteTarget by remember { mutableStateOf<InstalledSkill?>(null) }
    var error by remember { mutableStateOf<SkillError?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val visibleSkills = remember(skills, query) { filterSkills(skills, query) }

    LaunchedEffect(refreshKey) {
        loading = true
        repository.syncActiveSkills()
        skills = repository.list()
        loading = false
    }

    LaunchedEffect(Unit) {
        repository.changes.collect {
            skills = repository.list()
        }
    }

    LifecycleResumeEffect(Unit) {
        scope.launch {
            repository.syncIfChanged()
            skills = repository.list()
        }
        onPauseOrDispose { }
    }

    fun toggle(skill: InstalledSkill, enabled: Boolean) {
        if (busyId != null) return
        busyId = skill.id
        scope.launch {
            try {
                val ok = repository.setGlobalEnabled(skill.id, enabled)
                skills = repository.list()
                if (!ok) {
                    error = SkillError(R.string.skills_error_title, R.string.skills_error_io)
                }
            } finally {
                busyId = null
            }
        }
    }

    fun delete(skill: InstalledSkill) {
        if (busyId != null) return
        busyId = skill.id
        scope.launch {
            try {
                val ok = repository.delete(skill.id)
                if (selected?.id == skill.id) selected = null
                refreshKey++
                if (!ok) {
                    error = SkillError(R.string.skills_error_title, R.string.skills_error_io)
                }
            } finally {
                busyId = null
            }
        }
    }

    fun install(uri: Uri) {
        if (installing) return
        installing = true
        scope.launch {
            try {
                when (val read = readArchive(context, uri)) {
                    is ArchiveRead.Failure -> error = SkillError(
                        R.string.skills_install_failed_title,
                        read.messageRes,
                    )
                    is ArchiveRead.Success -> when (val result = repository.install(read.bytes, read.fallbackName)) {
                        is SkillArchiveResult.Failure -> error = SkillError(
                            R.string.skills_install_failed_title,
                            result.error.messageRes(),
                        )
                        is SkillArchiveResult.Success -> {
                            Toast.makeText(
                                context,
                                String.format(installSuccessTemplate, result.skill.name),
                                Toast.LENGTH_SHORT,
                            ).show()
                            refreshKey++
                        }
                    }
                }
            } finally {
                installing = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) install(uri)
    }
    val openPicker: () -> Unit = { picker.launch(arrayOf("*/*")) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("skills_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.skills_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.skills_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            if (installing) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    DdSpinner(size = 20.dp)
                }
            } else {
                DdIconButton(
                    icon = DdIcons.Plus,
                    onClick = openPicker,
                    contentDescription = stringResource(R.string.skills_install),
                    modifier = Modifier.testTag("skills_install"),
                    tint = colors.ink,
                    iconSize = 21.dp,
                )
            }
        }
        DdSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.skills_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "skills_search_field",
            clearTestTag = "skills_search_clear",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when {
                loading && skills.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(top = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DdSpinner(size = 22.dp)
                }

                skills.isEmpty() -> EmptySkills(onInstall = openPicker)

                visibleSkills.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(top = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_no_match),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }

                else -> {
                    Spacer(Modifier.height(8.dp))
                    SkillsGroup(
                        skills = visibleSkills,
                        enabled = busyId == null,
                        onOpen = { selected = it },
                        onToggle = { skill, enabled -> toggle(skill, enabled) },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    selected?.let { skill ->
        SkillDetailSheet(
            skill = skill,
            repository = repository,
            onDismiss = { selected = null },
            onDelete = { deleteTarget = skill },
        )
    }

    deleteTarget?.let { skill ->
        DdDialog(
            onDismissRequest = { deleteTarget = null },
            modifier = Modifier.testTag("skill_delete_dialog"),
            title = stringResource(R.string.skills_delete_title),
            content = {
                Text(
                    text = stringResource(R.string.skills_delete_message, skill.name),
                    style = DdType.bodySmall,
                    color = deepDeepColors().inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.skills_cancel),
                    onClick = { deleteTarget = null },
                    color = deepDeepColors().inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.skills_delete_confirm),
                    onClick = {
                        deleteTarget = null
                        delete(skill)
                    },
                    modifier = Modifier.testTag("skill_delete_confirm"),
                    color = deepDeepColors().danger,
                )
            },
        )
    }

    error?.let { current ->
        DdDialog(
            onDismissRequest = { error = null },
            modifier = Modifier.testTag("skills_error_dialog"),
            title = stringResource(current.titleRes),
            content = {
                Text(
                    text = stringResource(current.messageRes),
                    style = DdType.bodySmall,
                    color = deepDeepColors().inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.skills_close),
                    onClick = { error = null },
                    modifier = Modifier.testTag("skills_error_close"),
                )
            },
        )
    }
}

@Composable
private fun EmptySkills(onInstall: () -> Unit) {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DdIcon(icon = DdIcons.Sparkles, tint = colors.inkTertiary, size = 28.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.skills_empty_title),
            style = DdType.label,
            color = colors.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.skills_empty_hint),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(8.dp))
        DdTextAction(
            text = stringResource(R.string.skills_install_hint),
            onClick = onInstall,
            modifier = Modifier.testTag("skills_empty_install"),
            color = colors.accent,
        )
    }
}

@Composable
private fun SkillsGroup(
    skills: List<InstalledSkill>,
    enabled: Boolean,
    onOpen: (InstalledSkill) -> Unit,
    onToggle: (InstalledSkill, Boolean) -> Unit,
) {
    val colors = deepDeepColors()
    CompositionLocalProvider(LocalDdRowHorizontalPadding provides 16.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(vertical = 4.dp),
        ) {
            skills.forEachIndexed { index, skill ->
                if (index > 0) DdHairline()
                DdListRow(
                    title = skill.name,
                    subtitle = skill.description.takeIf { it.isNotBlank() },
                    subtitleMaxLines = 2,
                    icon = DdIcons.Sparkles,
                    onClick = { onOpen(skill) },
                    modifier = Modifier.testTag("skill_${skill.id}"),
                    trailing = {
                        if (skill.disableModelInvocation) {
                            ManualTag()
                            Spacer(Modifier.width(10.dp))
                        }
                        DdSwitch(
                            checked = skill.globalEnabled,
                            onCheckedChange = { onToggle(skill, it) },
                            enabled = enabled,
                            stateDescription = stringResource(
                                if (skill.globalEnabled) {
                                    R.string.skills_global_enabled
                                } else {
                                    R.string.skills_global_disabled
                                },
                            ),
                            modifier = Modifier.testTag("skill_global_${skill.id}"),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ManualTag() {
    val colors = deepDeepColors()
    Text(
        text = stringResource(R.string.skills_manual),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.wash)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = DdType.chip,
        color = colors.inkTertiary,
        maxLines = 1,
    )
}

@Composable
private fun SkillDetailSheet(
    skill: InstalledSkill,
    repository: SkillRepository,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = deepDeepColors()
    var content by remember(skill.id) { mutableStateOf<String?>(null) }
    var loading by remember(skill.id) { mutableStateOf(true) }
    LaunchedEffect(skill.id) {
        content = repository.readContent(skill.id)
        loading = false
    }
    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().testTag("skill_detail")) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = skill.name,
                    modifier = Modifier.weight(1f),
                    style = DdType.heading,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.skills_close),
                    tint = colors.inkSecondary,
                    iconSize = 20.dp,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (skill.description.isNotBlank()) {
                    Text(
                        text = skill.description,
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.skills_detail_status),
                        style = DdType.label,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            if (skill.globalEnabled) {
                                R.string.skills_detail_enabled
                            } else {
                                R.string.skills_detail_disabled
                            },
                        ),
                        style = DdType.bodySmall,
                        color = if (skill.globalEnabled) colors.accent else colors.inkTertiary,
                    )
                    if (skill.disableModelInvocation) {
                        Spacer(Modifier.width(10.dp))
                        ManualTag()
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.skills_detail_content),
                    style = DdType.micro,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.wash)
                        .padding(12.dp),
                ) {
                    when {
                        loading -> DdSpinner(size = 18.dp)
                        content.isNullOrBlank() -> Text(
                            text = stringResource(R.string.skills_detail_empty),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                        else -> Text(
                            text = content.orEmpty(),
                            style = DdType.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            ),
                            color = colors.inkSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                DdTextAction(
                    text = stringResource(R.string.skills_delete_confirm),
                    onClick = onDelete,
                    modifier = Modifier.testTag("skill_delete"),
                    color = colors.danger,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun SkillArchiveError.messageRes(): Int = when (this) {
    SkillArchiveError.NotZip -> R.string.skills_error_not_zip
    SkillArchiveError.Empty -> R.string.skills_error_empty
    SkillArchiveError.NoSkill -> R.string.skills_error_no_skill
    SkillArchiveError.MultipleSkills -> R.string.skills_error_multiple_skills
    SkillArchiveError.MissingDescription -> R.string.skills_error_missing_description
    SkillArchiveError.UnsafePath -> R.string.skills_error_unsafe_path
    SkillArchiveError.TooLarge -> R.string.skills_install_too_large
    SkillArchiveError.Io -> R.string.skills_error_io
}

private suspend fun readArchive(context: Context, uri: Uri): ArchiveRead = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var displayName: String? = null
    var size: Long? = null
    runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    }
    val declaredSize = size
    if (declaredSize != null && declaredSize > MAX_ARCHIVE_BYTES) {
        return@withContext ArchiveRead.Failure(R.string.skills_install_too_large)
    }
    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (total <= MAX_ARCHIVE_BYTES) {
                val read = stream.read(buffer, 0, minOf(buffer.size, MAX_ARCHIVE_BYTES + 1 - total))
                if (read < 0) break
                output.write(buffer, 0, read)
                total += read
            }
            output.toByteArray()
        }
    }.getOrNull() ?: return@withContext ArchiveRead.Failure(R.string.skills_install_unreadable)
    if (bytes.size > MAX_ARCHIVE_BYTES) {
        return@withContext ArchiveRead.Failure(R.string.skills_install_too_large)
    }
    val name = displayName
        ?.substringBeforeLast('.')
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?: "skill"
    ArchiveRead.Success(bytes, name)
}

internal fun filterSkills(skills: List<InstalledSkill>, query: String): List<InstalledSkill> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return skills
    return skills.filter { skill ->
        skill.name.contains(normalized, ignoreCase = true) ||
            skill.description.contains(normalized, ignoreCase = true) ||
            skill.id.contains(normalized, ignoreCase = true)
    }
}
