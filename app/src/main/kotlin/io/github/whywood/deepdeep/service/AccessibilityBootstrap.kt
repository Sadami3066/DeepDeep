package io.github.whywood.deepdeep.service

internal data class AccessibilitySettingsSnapshot(
    val enabledServices: String?,
    val accessibilityEnabled: Int?,
)

internal data class AccessibilityBootstrapPlan(
    val services: String?,
    val enableAccessibility: Boolean,
)

internal data class AccessibilityRebindPlan(
    val withoutService: String?,
    val withService: String,
)

internal data class AccessibilityComponent(
    val packageName: String,
    val className: String,
)

internal fun planAccessibilityBootstrap(
    component: String,
    enabledServices: String?,
    accessibilityEnabled: Int?,
): AccessibilityBootstrapPlan? {
    val current = enabledServices.orEmpty().trim()
    val merged = mergeAccessibilityServices(current, component)
    val needsServices = merged != current
    val needsEnabled = accessibilityEnabled != 1
    if (!needsServices && !needsEnabled) return null
    return AccessibilityBootstrapPlan(
        services = merged.takeIf { needsServices },
        enableAccessibility = needsEnabled,
    )
}

internal fun planAccessibilityRebind(component: String, enabledServices: String?): AccessibilityRebindPlan? {
    val target = parseAccessibilityComponent(component) ?: return null
    val services = enabledServices.orEmpty()
        .split(COMPONENT_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (services.none { parseAccessibilityComponent(it) == target }) return null
    val remaining = services.filter { parseAccessibilityComponent(it) != target }
    return AccessibilityRebindPlan(
        withoutService = remaining.joinToString(COMPONENT_SEPARATOR).takeIf { it.isNotEmpty() },
        withService = services.joinToString(COMPONENT_SEPARATOR),
    )
}

internal fun mergeAccessibilityServices(current: String?, component: String): String {
    val target = parseAccessibilityComponent(component) ?: return current.orEmpty().trim()
    val services = current.orEmpty()
        .split(COMPONENT_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (services.any { parseAccessibilityComponent(it) == target }) {
        return services.joinToString(COMPONENT_SEPARATOR)
    }
    return (services + component).joinToString(COMPONENT_SEPARATOR)
}

internal fun parseAccessibilityComponent(value: String): AccessibilityComponent? {
    val separator = value.indexOf(COMPONENT_PATH_SEPARATOR)
    if (separator <= 0 || separator == value.lastIndex) return null
    val packageName = value.substring(0, separator)
    val rawClassName = value.substring(separator + 1)
    val className = if (rawClassName.startsWith(".")) packageName + rawClassName else rawClassName
    return AccessibilityComponent(packageName, className)
}

internal fun parseAccessibilitySettings(output: String): AccessibilitySettingsSnapshot? {
    var enabledServices: String? = null
    var accessibilityEnabled: Int? = null
    var hasServices = false
    var hasEnabled = false
    for (line in output.lineSequence()) {
        when {
            line.startsWith(SERVICES_PREFIX) -> {
                hasServices = true
                enabledServices = line.removePrefix(SERVICES_PREFIX).trim().takeUnless {
                    it.isEmpty() || it == NULL_VALUE
                }
            }

            line.startsWith(ENABLED_PREFIX) -> {
                hasEnabled = true
                accessibilityEnabled = line.removePrefix(ENABLED_PREFIX).trim().toIntOrNull()
            }
        }
    }
    if (!hasServices || !hasEnabled) return null
    return AccessibilitySettingsSnapshot(
        enabledServices = enabledServices,
        accessibilityEnabled = accessibilityEnabled,
    )
}

internal fun accessibilityReadCommand(): String = listOf(
    "printf '$SERVICES_PREFIX'",
    "settings get secure $ENABLED_SERVICES_SETTING",
    "printf '$ENABLED_PREFIX'",
    "settings get secure $ACCESSIBILITY_ENABLED_SETTING",
).joinToString("; ")

internal fun accessibilityWriteCommand(plan: AccessibilityBootstrapPlan): String = buildList {
    plan.services?.let { services ->
        add("settings put secure $ENABLED_SERVICES_SETTING ${shellQuote(services)}")
    }
    if (plan.enableAccessibility) {
        add("settings put secure $ACCESSIBILITY_ENABLED_SETTING 1")
    }
}.joinToString(" && ")

internal fun accessibilityRebindCommand(plan: AccessibilityRebindPlan): String = buildList {
    add(
        plan.withoutService
            ?.let { "settings put secure $ENABLED_SERVICES_SETTING ${shellQuote(it)}" }
            ?: "settings delete secure $ENABLED_SERVICES_SETTING",
    )
    add("sleep 1")
    add("settings put secure $ENABLED_SERVICES_SETTING ${shellQuote(plan.withService)}")
    add("settings put secure $ACCESSIBILITY_ENABLED_SETTING 1")
}.joinToString(" && ")

internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private const val COMPONENT_SEPARATOR = ":"
private const val COMPONENT_PATH_SEPARATOR = "/"
private const val SERVICES_PREFIX = "S="
private const val ENABLED_PREFIX = "E="
private const val NULL_VALUE = "null"
private const val ENABLED_SERVICES_SETTING = "enabled_accessibility_services"
private const val ACCESSIBILITY_ENABLED_SETTING = "accessibility_enabled"
