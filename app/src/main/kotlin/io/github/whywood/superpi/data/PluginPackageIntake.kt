package io.github.whywood.superpi.data

object PluginPackageIntake {
    const val MIME = "application/vnd.superpi.plugin"
    const val SUFFIX = ".ddp"

    fun isPluginPackage(displayName: String?, mimeType: String?): Boolean {
        if (mimeType != null && mimeType.equals(MIME, ignoreCase = true)) return true
        return displayName?.endsWith(SUFFIX, ignoreCase = true) == true
    }
}
