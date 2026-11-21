package io.github.whywood.superpi.plugin.compat

enum class CompatibilitySeverity {
    ERROR,
    WARNING,
    INFO,
}

enum class CompatibilityCode {
    INVALID_ID,
    INVALID_VERSION,
    API_LEVEL_OUTDATED,
    ENTRY_CLASS_MISSING,
    MISSING_DEFAULT_LOCALE,
    MISSING_CAPABILITIES,
    UNKNOWN_CAPABILITY,
    PERMISSION_WITHOUT_CAPABILITY,
    COMPONENT_WITHOUT_CAPABILITY,
    INVALID_TOOL_NAME,
    RESERVED_TOOL_NAME,
    INVALID_SCHEMA,
    SCHEMA_MISSING_PROPERTIES,
    SCHEMA_NON_STANDARD_TYPES,
    EXPOSURE_OVERFLOW,
    RESULT_CONTRACT_VIOLATION,
}

data class RemediationOption(
    val id: String,
    val title: String,
    val description: String,
    val isRecommended: Boolean = false,
)

data class CompatibilityIssue(
    val code: CompatibilityCode,
    val field: String,
    val message: String,
    val severity: CompatibilitySeverity,
    val options: List<RemediationOption> = emptyList(),
)

data class CompatibilityReport(
    val isCompatible: Boolean,
    val issues: List<CompatibilityIssue>,
) {
    val errors: List<CompatibilityIssue> get() = issues.filter { it.severity == CompatibilitySeverity.ERROR }
    val warnings: List<CompatibilityIssue> get() = issues.filter { it.severity == CompatibilitySeverity.WARNING }
    val recommendedFixCount: Int get() = issues.count { it.options.any { opt -> opt.isRecommended } }
}
