package io.github.whywood.deepdeep.runtime

import java.io.File
import java.io.IOException
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath

class ScopedFileSystem(
    delegate: FileSystem,
    allowedRoots: List<Path>,
) : ForwardingFileSystem(delegate) {
    private val roots: List<Path> = allowedRoots.map { root -> canonicalForCheck(root) }

    override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        val canonical = canonicalForCheck(path)
        if (roots.none { root -> isWithin(canonical, root) }) {
            throw IOException("path outside workshop workspace: $path")
        }
        return canonical
    }

    private fun canonicalForCheck(path: Path): Path {
        runCatching { delegate.canonicalize(path) }.getOrNull()?.let { return it }
        val normalized = path.normalized()
        val canonical = runCatching { File(normalized.toString()).canonicalPath }.getOrNull()
            ?: return normalized
        return canonical.toPath()
    }

    private fun isWithin(path: Path, root: Path): Boolean {
        val pathSegments = path.segments
        val rootSegments = root.segments
        if (pathSegments.size < rootSegments.size) return false
        return rootSegments.indices.all { index -> pathSegments[index] == rootSegments[index] }
    }
}
