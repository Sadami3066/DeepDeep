package io.github.whywood.deepdeep.plugin.api

object PluginValidation {
    enum class Severity {
        ERROR,
        WARNING,
    }

    enum class Code {
        INVALID_ID,
        INVALID_VERSION,
        INVALID_MIN_HOST_VERSION,
        API_LEVEL_UNSUPPORTED,
        UI_API_LEVEL_UNSUPPORTED,
        HOST_VERSION_UNSUPPORTED,
        ENTRY_CLASS_MISSING,
        INVALID_ENTRY_CLASS,
        MISSING_TEXT,
        INVALID_TEXT,
        INVALID_ICON,
        INVALID_URL,
        INVALID_PROVIDES,
        INVALID_UI_SLOT,
        INVALID_SKILL,
    }

    data class Issue(
        val code: Code,
        val field: String,
        val message: String,
        val severity: Severity = Severity.ERROR,
    )

    const val MAX_ID_LENGTH: Int = 128

    private val PLUGIN_ID = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    private val ENTRY_CLASS = Regex("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+$")

    private val TOOL_NAME = Regex("^[a-z][a-z0-9_]*$")

    private val PROVIDES_ID = Regex("^[a-z][A-Za-z0-9_-]*$")

    private val PAGE_ID = Regex("^[a-z][a-z0-9_.-]*$")

    private val SKILL_ID = Regex("^[a-z][a-z0-9_-]*$")

    fun isValidId(id: String): Boolean = id.length in 1..MAX_ID_LENGTH && PLUGIN_ID.matches(id)

    fun isValidEntryClass(value: String): Boolean = value.length <= 256 && ENTRY_CLASS.matches(value)

    fun isValidToolName(value: String): Boolean = TOOL_NAME.matches(value)

    fun validate(
        manifest: PluginManifest,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ): List<Issue> = buildList {
        if (!isValidId(manifest.id)) {
            add(error(Code.INVALID_ID, "id", "invalid plugin id: ${manifest.id}"))
        }
        if (PluginVersion.parse(manifest.version) == null) {
            add(error(Code.INVALID_VERSION, "version", "version must be major.minor.patch with optional suffix: ${manifest.version}"))
        }
        if (manifest.apiLevel !in 1..hostApiLevel) {
            add(error(Code.API_LEVEL_UNSUPPORTED, "apiLevel", "apiLevel ${manifest.apiLevel} is outside the supported range 1..$hostApiLevel"))
        }
        manifest.minHostVersion?.let { raw ->
            val minimum = PluginVersion.parse(raw)
            if (minimum == null) {
                add(error(Code.INVALID_MIN_HOST_VERSION, "minHostVersion", "minHostVersion is not a valid version: $raw"))
            } else {
                val host = hostVersion?.let(PluginVersion::parse)
                if (host != null && host.compareCore(minimum) < 0) {
                    add(error(Code.HOST_VERSION_UNSUPPORTED, "minHostVersion", "host $hostVersion is older than required $minimum"))
                }
            }
        }
        manifest.ui?.let { ui ->
            if (ui.apiLevel !in 1..HOST_UI_API_LEVEL) {
                add(
                    Issue(
                        Code.UI_API_LEVEL_UNSUPPORTED,
                        "ui.apiLevel",
                        "ui apiLevel ${ui.apiLevel} is outside the supported range 1..$HOST_UI_API_LEVEL, compose slots will be disabled",
                        Severity.WARNING,
                    ),
                )
            }
        }
        validateText(manifest.name, "name", required = true)
        validateText(manifest.summary, "summary", required = true)
        manifest.description?.let { validateText(it, "description", required = false) }
        manifest.homepage?.let { url ->
            if (!isHttpUrl(url)) add(error(Code.INVALID_URL, "homepage", "homepage must be an http(s) url: $url"))
        }
        manifest.icon?.let { validateIcon(it) }
        val entry = manifest.entryClass
        when {
            entry.isNullOrBlank() -> add(error(Code.ENTRY_CLASS_MISSING, "entryClass", "plugin requires entryClass"))
            !isValidEntryClass(entry) -> add(error(Code.INVALID_ENTRY_CLASS, "entryClass", "entryClass is not a valid class name: $entry"))
        }
        validateProvides(manifest.provides)
    }

    fun validateSkill(skill: PluginSkill): List<Issue> = buildList {
        if (!SKILL_ID.matches(skill.id)) add(error(Code.INVALID_SKILL, "skill.id", "invalid skill id: ${skill.id}"))
        if (skill.markdown.isBlank()) add(error(Code.INVALID_SKILL, "skill.markdown", "skill markdown must not be blank"))
        val seen = mutableSetOf<String>()
        skill.files.forEachIndexed { index, file ->
            val field = "skill.files[$index]"
            if (!isSafeRelativePath(file.path)) {
                add(error(Code.INVALID_SKILL, "$field.path", "file path must be a relative path inside the bundle: ${file.path}"))
            } else if (!seen.add(file.path)) {
                add(error(Code.INVALID_SKILL, "$field.path", "duplicate file path: ${file.path}"))
            }
            if ((file.content != null) == (file.assetPath != null)) {
                add(error(Code.INVALID_SKILL, field, "exactly one of content or assetPath is required"))
            }
            file.assetPath?.let { asset ->
                if (!isSafeRelativePath(asset)) {
                    add(error(Code.INVALID_SKILL, "$field.assetPath", "asset path must be a relative path inside the bundle: $asset"))
                }
            }
        }
    }

    fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith("/") &&
            !path.contains("..") &&
            !path.contains('\\') &&
            !path.contains(':') &&
            path.length <= 512

    private fun MutableList<Issue>.validateText(
        text: LocalizedText,
        field: String,
        required: Boolean,
    ) {
        when (text) {
            is LocalizedText.Resource -> add(error(Code.INVALID_TEXT, field, "plugin cannot reference android resources"))
            is LocalizedText.Literal -> {
                if (!text.hasDefault()) {
                    add(
                        Issue(
                            if (required) Code.MISSING_TEXT else Code.INVALID_TEXT,
                            field,
                            "missing \"$DEFAULT_LANGUAGE\" text",
                            if (required) Severity.ERROR else Severity.WARNING,
                        ),
                    )
                }
                if (text.values.keys.any { it.isBlank() }) {
                    add(warning(Code.INVALID_TEXT, field, "language keys must not be blank"))
                }
            }
        }
    }

    private fun MutableList<Issue>.validateIcon(icon: IconRef) {
        when (icon) {
            is IconRef.Resource -> add(error(Code.INVALID_ICON, "icon", "plugin cannot reference android resources"))
            is IconRef.Asset -> if (!isSafeRelativePath(icon.path)) {
                add(error(Code.INVALID_ICON, "icon", "icon must be a relative asset path inside the bundle: ${icon.path}"))
            }
        }
    }

    private fun MutableList<Issue>.validateProvides(provides: Provides) {
        validateIds(provides.tools, "provides.tools", TOOL_NAME)
        validateIds(provides.skills, "provides.skills", PROVIDES_ID)
        validateIds(provides.pages, "provides.pages", PAGE_ID)
        provides.uiSlots.forEachIndexed { index, slot ->
            if (!SlotIds.matches(slot)) {
                add(error(Code.INVALID_UI_SLOT, "provides.uiSlots[$index]", "unknown or incomplete slot id: $slot"))
            }
        }
    }

    private fun MutableList<Issue>.validateIds(values: List<String>, field: String, pattern: Regex) {
        val seen = mutableSetOf<String>()
        values.forEachIndexed { index, value ->
            when {
                !pattern.matches(value) -> add(error(Code.INVALID_PROVIDES, "$field[$index]", "invalid identifier: $value"))
                !seen.add(value) -> add(error(Code.INVALID_PROVIDES, "$field[$index]", "duplicate identifier: $value"))
            }
        }
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("http://") || value.startsWith("https://")

    private fun error(code: Code, field: String, message: String): Issue =
        Issue(code, field, message, Severity.ERROR)

    private fun warning(code: Code, field: String, message: String): Issue =
        Issue(code, field, message, Severity.WARNING)
}
