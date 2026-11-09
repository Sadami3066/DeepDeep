package io.github.whywood.deepdeep.plugin.manager

import java.io.File

data class ExternalInspectionUi(
    val file: File,
    val pluginId: String,
    val name: String,
    val version: String,
    val size: Long,
    val sha256: String,
    val publisherName: String,
    val publisherKeyId: String,
    val trusted: Boolean,
    val requiresPrivilege: Boolean,
    val provides: io.github.whywood.deepdeep.plugin.api.Provides,
    val capabilities: Set<String>,
    val error: String?,
)

data class ExternalInstallUi(
    val success: Boolean,
    val error: String?,
    val requiresConfirmation: Boolean = false,
    val restartRequired: Boolean = false,
)
