package io.github.whywood.superpi.runtime

fun interface SessionTitleNamer {
    suspend fun title(firstUserText: String): String?
}
