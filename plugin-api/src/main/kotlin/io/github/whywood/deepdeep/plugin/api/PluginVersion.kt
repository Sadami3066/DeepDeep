package io.github.whywood.deepdeep.plugin.api

data class PluginVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val suffix: String? = null,
) : Comparable<PluginVersion> {
    override fun compareTo(other: PluginVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch${suffix.orEmpty()}"

    companion object {
        private val PATTERN = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)([-+][0-9A-Za-z][0-9A-Za-z.-]*)?$")

        fun parse(raw: String): PluginVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            return PluginVersion(major, minor, patch, match.groupValues[4].ifEmpty { null })
        }

        fun compare(first: String, second: String): Int? {
            val left = parse(first) ?: return null
            val right = parse(second) ?: return null
            return left.compareTo(right)
        }
    }
}
