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
        INVALID_REQUIRES,
        INVALID_PROVIDES,
        INVALID_UI_SLOT,
        INVALID_SKILL,
        INVALID_UI_NODE,
    }

    data class Issue(
        val code: Code,
        val field: String,
        val message: String,
        val severity: Severity = Severity.ERROR,
    )

    const val MAX_ID_LENGTH: Int = 128

    const val MAX_UI_DEPTH: Int = 8

    const val MAX_UI_NODES: Int = 200

    private val EXTERNAL_ID = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

    private val BUILTIN_ID = Regex("^[a-z][a-z0-9_-]*$")

    private val ENTRY_CLASS = Regex("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+$")

    private val TOOL_NAME = Regex("^[a-z][a-z0-9_]*$")

    private val PROVIDES_ID = Regex("^[a-z][A-Za-z0-9_-]*$")

    private val SKILL_ID = Regex("^[a-z][a-z0-9_-]*$")

    private val TEXT_STYLES = setOf("body", "title", "label", "caption", "code")

    private val PROGRESS_BOUNDS = 0..100

    private val SPACER_BOUNDS = 0..480

    private class NodeRule(
        val requiredProps: Set<String> = emptySet(),
        val optionalProps: Set<String> = emptySet(),
        val text: Boolean = false,
        val children: Boolean = false,
    )

    private val NODE_RULES: Map<String, NodeRule> = mapOf(
        UiNodeTypes.COLUMN to NodeRule(children = true),
        UiNodeTypes.ROW to NodeRule(children = true),
        UiNodeTypes.TEXT to NodeRule(optionalProps = setOf("style"), text = true),
        UiNodeTypes.MARKDOWN to NodeRule(text = true),
        UiNodeTypes.IMAGE to NodeRule(requiredProps = setOf("source"), optionalProps = setOf("content_description")),
        UiNodeTypes.BUTTON to NodeRule(requiredProps = setOf("action"), text = true),
        UiNodeTypes.LINK to NodeRule(requiredProps = setOf("url"), text = true),
        UiNodeTypes.PROGRESS to NodeRule(optionalProps = setOf("value")),
        UiNodeTypes.QR_CODE to NodeRule(requiredProps = setOf("data")),
        UiNodeTypes.DIVIDER to NodeRule(),
        UiNodeTypes.SPACER to NodeRule(optionalProps = setOf("height")),
    )

    fun isValidExternalId(id: String): Boolean = id.length in 1..MAX_ID_LENGTH && EXTERNAL_ID.matches(id)

    fun isValidBuiltinId(id: String): Boolean = id.length in 1..MAX_ID_LENGTH && BUILTIN_ID.matches(id)

    fun isValidEntryClass(value: String): Boolean = value.length <= 256 && ENTRY_CLASS.matches(value)

    fun isValidToolName(value: String): Boolean = TOOL_NAME.matches(value)

    fun isCompatible(
        manifest: PluginManifest,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ): Boolean {
        if (manifest.apiLevel !in 1..hostApiLevel) return false
        val uiLevel = manifest.ui?.apiLevel
        if (uiLevel != null && uiLevel !in 1..HOST_UI_API_LEVEL) return false
        val minimum = manifest.minHostVersion?.let(PluginVersion::parse) ?: return manifest.minHostVersion == null
        val host = hostVersion?.let(PluginVersion::parse) ?: return true
        return host >= minimum
    }

    fun validate(
        manifest: PluginManifest,
        source: PluginSource = manifest.source,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ): List<Issue> = buildList {
        val external = source == PluginSource.EXTERNAL
        val idValid = if (external) isValidExternalId(manifest.id) else isValidBuiltinId(manifest.id)
        if (!idValid) {
            add(error(Code.INVALID_ID, "id", "invalid ${source.name.lowercase()} plugin id: ${manifest.id}"))
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
                if (host != null && host < minimum) {
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
        validateText(manifest.name, "name", required = true, external = external)
        validateText(manifest.summary, "summary", required = true, external = external)
        manifest.description?.let { validateText(it, "description", required = false, external = external) }
        manifest.sizeLabel?.let { validateText(it, "sizeLabel", required = false, external = external) }
        manifest.homepage?.let { url ->
            if (!isHttpUrl(url)) add(error(Code.INVALID_URL, "homepage", "homepage must be an http(s) url: $url"))
        }
        manifest.icon?.let { validateIcon(it, external) }
        if (external) {
            val entry = manifest.entryClass
            when {
                entry.isNullOrBlank() -> add(error(Code.ENTRY_CLASS_MISSING, "entryClass", "external plugin requires entryClass"))
                !isValidEntryClass(entry) -> add(error(Code.INVALID_ENTRY_CLASS, "entryClass", "entryClass is not a valid class name: $entry"))
            }
        }
        manifest.requires.forEachIndexed { index, value ->
            if (value.isBlank()) add(error(Code.INVALID_REQUIRES, "requires[$index]", "requires entry must not be blank"))
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

    fun validateUiNode(node: UiNode): List<Issue> {
        val issues = mutableListOf<Issue>()
        var visited = 0
        var depthReported = false
        fun walk(current: UiNode, path: String, depth: Int) {
            visited += 1
            if (visited > MAX_UI_NODES) {
                if (visited == MAX_UI_NODES + 1) {
                    issues += error(Code.INVALID_UI_NODE, path, "too many ui nodes (max $MAX_UI_NODES)")
                }
                return
            }
            if (depth > MAX_UI_DEPTH) {
                if (!depthReported) {
                    depthReported = true
                    issues += error(Code.INVALID_UI_NODE, path, "ui tree is nested too deep (max $MAX_UI_DEPTH)")
                }
                return
            }
            validateNode(current, path, issues)
            current.children.forEachIndexed { index, child -> walk(child, "$path.children[$index]", depth + 1) }
        }
        walk(node, "node", 0)
        return issues
    }

    fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith("/") &&
            !path.contains("..") &&
            !path.contains('\\') &&
            !path.contains(':') &&
            path.length <= 512

    private fun validateNode(node: UiNode, path: String, issues: MutableList<Issue>) {
        val rule = NODE_RULES[node.type]
        if (rule == null) {
            issues += error(Code.INVALID_UI_NODE, path, "unsupported node type: ${node.type}")
            return
        }
        if (rule.text && node.text.isNullOrBlank()) {
            issues += error(Code.INVALID_UI_NODE, "$path.text", "node type ${node.type} requires text")
        }
        if (!rule.text && !node.text.isNullOrBlank()) {
            issues += warning(Code.INVALID_UI_NODE, "$path.text", "node type ${node.type} ignores text")
        }
        if (!rule.children && node.children.isNotEmpty()) {
            issues += error(Code.INVALID_UI_NODE, "$path.children", "node type ${node.type} cannot have children")
        }
        val allowed = rule.requiredProps + rule.optionalProps
        val unknown = node.props.keys - allowed
        if (unknown.isNotEmpty()) {
            issues += warning(Code.INVALID_UI_NODE, "$path.props", "unsupported props for ${node.type}: ${unknown.sorted().joinToString()}")
        }
        rule.requiredProps.forEach { prop ->
            if (node.props[prop].isNullOrBlank()) {
                issues += error(Code.INVALID_UI_NODE, "$path.props.$prop", "node type ${node.type} requires prop $prop")
            }
        }
        when (node.type) {
            UiNodeTypes.TEXT -> node.props["style"]?.let { style ->
                if (style !in TEXT_STYLES) {
                    issues += error(Code.INVALID_UI_NODE, "$path.props.style", "unsupported text style: $style")
                }
            }
            UiNodeTypes.IMAGE -> node.props["source"]?.let { source ->
                if (!isImageSource(source)) {
                    issues += error(Code.INVALID_UI_NODE, "$path.props.source", "image source must be an http(s) url or an asset path: $source")
                }
            }
            UiNodeTypes.BUTTON -> node.props["action"]?.let { action ->
                if (!PROVIDES_ID.matches(action)) {
                    issues += error(Code.INVALID_UI_NODE, "$path.props.action", "invalid action id: $action")
                }
            }
            UiNodeTypes.LINK -> node.props["url"]?.let { url ->
                if (!isLinkUrl(url)) {
                    issues += error(Code.INVALID_UI_NODE, "$path.props.url", "link must be an http(s) or mailto url: $url")
                }
            }
            UiNodeTypes.PROGRESS -> node.props["value"]?.let { value ->
                val parsed = value.toIntOrNull()
                if (parsed == null || parsed !in PROGRESS_BOUNDS) {
                    issues += error(Code.INVALID_UI_NODE, "$path.props.value", "progress value must be an integer in $PROGRESS_BOUNDS: $value")
                }
            }
            UiNodeTypes.SPACER -> node.props["height"]?.let { value ->
                val parsed = value.toIntOrNull()
                if (parsed == null || parsed !in SPACER_BOUNDS) {
                    issues += error(Code.INVALID_UI_NODE, "$path.props.height", "spacer height must be an integer in $SPACER_BOUNDS: $value")
                }
            }
        }
    }

    private fun MutableList<Issue>.validateText(
        text: LocalizedText,
        field: String,
        required: Boolean,
        external: Boolean,
    ) {
        when (text) {
            is LocalizedText.Resource -> if (external) {
                add(error(Code.INVALID_TEXT, field, "external plugin cannot reference android resources"))
            }
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

    private fun MutableList<Issue>.validateIcon(icon: IconRef, external: Boolean) {
        when (icon) {
            is IconRef.Resource -> if (external) {
                add(error(Code.INVALID_ICON, "icon", "external plugin cannot reference android resources"))
            }
            is IconRef.Asset -> if (!isSafeRelativePath(icon.path)) {
                add(error(Code.INVALID_ICON, "icon", "icon must be a relative asset path inside the bundle: ${icon.path}"))
            }
        }
    }

    private fun MutableList<Issue>.validateProvides(provides: Provides) {
        validateIds(provides.tools, "provides.tools", TOOL_NAME)
        validateIds(provides.skills, "provides.skills", PROVIDES_ID)
        validateIds(provides.settings, "provides.settings", PROVIDES_ID)
        validateIds(provides.actions, "provides.actions", PROVIDES_ID)
        validateIds(provides.events, "provides.events", PROVIDES_ID)
        validateIds(provides.pages, "provides.pages", PROVIDES_ID)
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

    private fun isImageSource(value: String): Boolean =
        isHttpUrl(value) || (value.startsWith("asset:") && isSafeRelativePath(value.removePrefix("asset:")))

    private fun isLinkUrl(value: String): Boolean =
        isHttpUrl(value) || (value.startsWith("mailto:") && value.length > "mailto:".length)

    private fun error(code: Code, field: String, message: String): Issue =
        Issue(code, field, message, Severity.ERROR)

    private fun warning(code: Code, field: String, message: String): Issue =
        Issue(code, field, message, Severity.WARNING)
}
