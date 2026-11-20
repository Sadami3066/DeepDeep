package io.github.whywood.superpi.runtime

enum class MemoryWriteMode {
    APPEND,
    REPLACE_SECTION,
    OVERWRITE,
    ;

    companion object {
        fun fromWire(value: String?): MemoryWriteMode? = when (value?.trim()?.lowercase()) {
            null, "", "append" -> APPEND
            "replace_section", "replace-section", "replace" -> REPLACE_SECTION
            "overwrite" -> OVERWRITE
            else -> null
        }
    }
}

sealed interface MemoryWriteOutcome {
    data object Applied : MemoryWriteOutcome

    data object Queued : MemoryWriteOutcome

    data object Inactive : MemoryWriteOutcome

    data class Rejected(val message: String) : MemoryWriteOutcome
}

interface MemoryBackend {
    suspend fun append(path: String, section: String?, text: String): MemoryWriteOutcome

    suspend fun replaceSection(path: String, section: String, text: String): MemoryWriteOutcome

    suspend fun overwrite(path: String, text: String): MemoryWriteOutcome

    suspend fun refreshIfStale(maxAgeMillis: Long = DEFAULT_REFRESH_AGE): Boolean
}

const val DEFAULT_REFRESH_AGE = 30_000L
