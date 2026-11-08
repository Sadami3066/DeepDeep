package io.github.whywood.deepdeep.ui.workspaces

import io.github.whywood.deepdeep.data.PUBLIC_STORAGE_ROOT

internal fun sharedStorageDirectory(authority: String?, documentId: String?): String? {
    if (authority != "com.android.externalstorage.documents") return null
    val id = documentId ?: return null
    val separator = id.indexOf(':')
    if (separator <= 0) return null
    if (id.substring(0, separator) != "primary") return null
    val relative = id.substring(separator + 1).trim('/')
    if (relative.isEmpty()) return null
    if (relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
    return "$PUBLIC_STORAGE_ROOT/$relative"
}
