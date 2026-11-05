package io.github.whywood.deepdeep.plugin.host.ui

internal data class DetectedComposeVersion(
    val version: String?,
    val compatibilityId: Int?,
)

internal object ComposeVersionDetector {
    private const val VERSION_ENTRY = "META-INF/androidx.compose.runtime_runtime.version"

    private const val VERSION_CLASS = "androidx.compose.runtime.ComposeVersion"

    fun detect(): DetectedComposeVersion = detectFromResource() ?: detectFromClass() ?: DetectedComposeVersion(null, null)

    internal fun detectFromResource(read: (String) -> String? = ::readClasspathText): DetectedComposeVersion? =
        normalizeVersion(read(VERSION_ENTRY))?.let { DetectedComposeVersion(it, null) }

    internal fun detectFromClass(load: (String) -> Class<*>? = ::loadClass): DetectedComposeVersion? =
        load(VERSION_CLASS)?.let { type ->
            when (val raw = runCatching { type.getField("version").get(null) }.getOrNull()) {
                is String -> normalizeVersion(raw)?.let { DetectedComposeVersion(it, null) }
                is Int -> DetectedComposeVersion(null, raw)
                else -> null
            }
        }

    internal fun normalizeVersion(raw: String?): String? =
        raw?.trim()?.removePrefix("v")?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun readClasspathText(entry: String): String? = runCatching {
        ComposeVersionDetector::class.java.classLoader
            ?.getResourceAsStream(entry)
            ?.bufferedReader()
            ?.use { it.readText() }
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun loadClass(name: String): Class<*>? = runCatching {
        Class.forName(name, false, ComposeVersionDetector::class.java.classLoader)
    }.getOrNull()
}
