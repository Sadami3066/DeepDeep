package io.github.whywood.deepdeep.runtime

fun interface SessionTitleNamer {
    suspend fun title(firstUserText: String): String?
}
