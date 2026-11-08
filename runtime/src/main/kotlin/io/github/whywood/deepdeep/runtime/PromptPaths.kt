package io.github.whywood.deepdeep.runtime

data class StorageMountInfo(
    val name: String,
    val description: String,
    val path: String,
)

internal fun buildPathsSection(
    skillsDir: String,
    appDataDir: String,
    sharedStorage: Boolean,
    mounts: List<StorageMountInfo>,
): String = buildString {
    appendLine("Skills: $skillsDir.")
    appendLine("App data: $appDataDir (/data/data equivalent). Bash and file tools use these real paths.")
    if (sharedStorage) {
        appendLine(
            "Shared storage: $PUBLIC_STORAGE_ROOT — all user files are readable and writable. " +
                "Do not delete or overwrite user files unless the user asks.",
        )
    }
    if (mounts.isNotEmpty()) {
        appendLine("Mounted directories:")
        mounts.forEach { mount ->
            append("- ")
            append(mount.name)
            append(" (")
            append(mount.path)
            append(")")
            val description = mount.description.trim()
            if (description.isNotEmpty()) {
                append(": ")
                append(description)
            }
            appendLine()
        }
    }
}.trim()

internal const val PUBLIC_STORAGE_ROOT = "/storage/emulated/0"
