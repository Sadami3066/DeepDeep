package io.github.whywood.deepdeep.plugin.host.external

internal data class PluginVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val suffix: String? = null,
) : Comparable<PluginVersion> {
    fun compareCore(other: PluginVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun compareTo(other: PluginVersion): Int {
        val core = compareCore(other)
        if (core != 0) return core
        val left = prerelease()
        val right = other.prerelease()
        return when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            else -> comparePrerelease(left, right)
        }
    }

    override fun toString(): String = "$major.$minor.$patch${suffix.orEmpty()}"

    private fun prerelease(): String? = suffix?.takeIf { it.startsWith("-") }?.removePrefix("-")

    private fun comparePrerelease(left: String, right: String): Int {
        val leftParts = left.split('.')
        val rightParts = right.split('.')
        for (index in 0 until minOf(leftParts.size, rightParts.size)) {
            val result = compareIdentifier(leftParts[index], rightParts[index])
            if (result != 0) return result
        }
        return leftParts.size.compareTo(rightParts.size)
    }

    private fun compareIdentifier(left: String, right: String): Int {
        val leftNumeric = left.all { it in '0'..'9' }
        val rightNumeric = right.all { it in '0'..'9' }
        return when {
            leftNumeric && rightNumeric -> compareNumeric(left, right)
            leftNumeric -> -1
            rightNumeric -> 1
            else -> left.compareTo(right)
        }
    }

    private fun compareNumeric(left: String, right: String): Int {
        val leftDigits = left.trimStart('0').ifEmpty { "0" }
        val rightDigits = right.trimStart('0').ifEmpty { "0" }
        if (leftDigits.length != rightDigits.length) return leftDigits.length.compareTo(rightDigits.length)
        return leftDigits.compareTo(rightDigits)
    }

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
