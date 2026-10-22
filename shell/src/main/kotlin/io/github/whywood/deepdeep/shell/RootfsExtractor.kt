package io.github.whywood.deepdeep.shell

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission

class RootfsExtractor {
    fun extract(tarStream: InputStream, target: Path): Int {
        val root = target.toAbsolutePath().normalize()
        Files.createDirectories(root)
        val rootReal = root.toRealPath()
        val directoryModes = mutableListOf<Pair<Path, Int>>()
        var extracted = 0

        val header = ByteArray(HEADER_SIZE)
        while (true) {
            val headerBytes = readFully(tarStream, header)
            if (headerBytes == 0) break
            require(headerBytes == HEADER_SIZE) { "truncated tar header" }
            if (header.all { it == 0.toByte() }) break
            verifyChecksum(header)

            val name = readString(header, 0, 100)
            val prefix = readString(header, 345, 155)
            val fullName = if (prefix.isEmpty()) name else "$prefix/$name"
            val mode = readOctal(header, 100, 8).toInt()
            val size = readOctal(header, 124, 12)
            val type = header[156].toInt().toChar()
            val linkName = readString(header, 157, 100)
            require(size in 0..MAX_ENTRY_BYTES) { "tar entry too large: $fullName" }

            val relative = safeRelative(fullName)
            if (relative == null) {
                skipEntry(tarStream, size)
                continue
            }
            val destination = root.resolve(relative).normalize()
            require(destination.startsWith(root)) { "tar entry escapes rootfs: $fullName" }

            when (type) {
                '5' -> {
                    ensureDirectory(root, destination)
                    directoryModes += destination to mode
                    skipEntry(tarStream, size)
                }

                '2' -> {
                    ensureParent(root, rootReal, destination)
                    requireAbsent(destination)
                    val linkTarget = resolveLinkTarget(root, destination, linkName)
                    val relativeTarget = destination.parent.relativize(linkTarget)
                    require(relativeTarget.toString().isNotEmpty()) { "empty symlink target: $fullName" }
                    Files.createSymbolicLink(destination, relativeTarget)
                    skipEntry(tarStream, size)
                }

                '0', '\u0000' -> {
                    ensureParent(root, rootReal, destination)
                    requireAbsent(destination)
                    Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW).use { output ->
                        copyEntry(tarStream, output, size)
                    }
                    setMode(destination, mode)
                }

                else -> throw IllegalArgumentException("unsupported tar entry type '$type': $fullName")
            }
            extracted++
        }

        directoryModes.sortedByDescending { it.first.nameCount }.forEach { (path, mode) ->
            setMode(path, mode)
        }
        setMode(root, 0x1ED)
        return extracted
    }

    internal fun safeRelative(name: String): Path? {
        val cleaned = name.removePrefix("./").trimEnd('/')
        if (cleaned.isEmpty() || cleaned == ".") return null
        require(!cleaned.startsWith("/")) { "absolute tar entry: $name" }
        require('\u0000' !in cleaned) { "NUL in tar entry: $name" }
        val relative = Paths.get(cleaned).normalize()
        require(!relative.isAbsolute && relative.nameCount > 0) { "invalid tar entry: $name" }
        require(relative.first().toString() != "..") { "tar entry escapes rootfs: $name" }
        return relative
    }

    private fun ensureParent(root: Path, rootReal: Path, path: Path) {
        val parent = path.parent ?: throw IllegalArgumentException("entry has no parent: $path")
        var current = root
        for (part in root.relativize(parent)) {
            current = current.resolve(part)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(current)) { "symlink in parent path: $current" }
                require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) { "not a directory: $current" }
            } else {
                Files.createDirectory(current)
            }
        }
        require(parent.toRealPath().startsWith(rootReal)) { "parent escapes rootfs: $parent" }
    }

    private fun ensureDirectory(root: Path, path: Path) {
        ensureParent(root, root.toRealPath(), path)
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(path)) { "directory path is a symlink: $path" }
            require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { "not a directory: $path" }
        } else {
            Files.createDirectory(path)
        }
    }

    private fun requireAbsent(path: Path) {
        require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { "duplicate tar entry: $path" }
    }

    private fun resolveLinkTarget(root: Path, link: Path, rawTarget: String): Path {
        require(rawTarget.isNotBlank()) { "empty symlink target: $link" }
        require('\u0000' !in rawTarget) { "NUL in symlink target: $rawTarget" }
        val raw = Paths.get(rawTarget)
        val resolved = if (raw.isAbsolute) {
            root.resolve(raw.toString().removePrefix("/")).normalize()
        } else {
            link.parent.resolve(raw).normalize()
        }
        require(resolved.startsWith(root)) { "symlink escapes rootfs: $link -> $rawTarget" }
        require(resolved != link) { "self-referential symlink: $link" }
        return resolved
    }

    private fun verifyChecksum(header: ByteArray) {
        val expected = readOctal(header, 148, 8)
        var actual = 0L
        for (index in header.indices) {
            actual += if (index in 148..155) 32 else header[index].toLong() and 0xFF
        }
        require(actual == expected) { "invalid tar header checksum: expected=$expected actual=$actual" }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun copyEntry(input: InputStream, output: java.io.OutputStream, size: Long) {
        val buffer = ByteArray(8192)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IllegalArgumentException("truncated tar entry")
            output.write(buffer, 0, read)
            remaining -= read
        }
        skipBytes(input, (HEADER_SIZE - size % HEADER_SIZE) % HEADER_SIZE)
    }

    private fun skipEntry(input: InputStream, size: Long) {
        val padded = (size + HEADER_SIZE - 1) / HEADER_SIZE * HEADER_SIZE
        skipBytes(input, padded)
    }

    private fun skipBytes(input: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IllegalArgumentException("truncated tar entry")
            remaining -= read
        }
    }

    private fun readString(header: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { header[it] == 0.toByte() } ?: (offset + length)
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    private fun readOctal(header: ByteArray, offset: Int, length: Int): Long {
        val text = String(header, offset, length, Charsets.US_ASCII).trim('\u0000', ' ')
        return if (text.isEmpty()) 0L else text.toLong(8)
    }

    private fun setMode(path: Path, mode: Int) {
        val permissions = modeToPermissions(mode)
        val applied = runCatching {
            Files.setAttribute(path, "unix:mode", mode and 0x0FFF)
        }.isSuccess
        if (!applied) Files.setPosixFilePermissions(path, permissions)
    }

    private fun modeToPermissions(mode: Int): Set<PosixFilePermission> = buildSet {
        if (mode and 0x100 != 0) add(PosixFilePermission.OWNER_READ)
        if (mode and 0x080 != 0) add(PosixFilePermission.OWNER_WRITE)
        if (mode and 0x040 != 0) add(PosixFilePermission.OWNER_EXECUTE)
        if (mode and 0x020 != 0) add(PosixFilePermission.GROUP_READ)
        if (mode and 0x010 != 0) add(PosixFilePermission.GROUP_WRITE)
        if (mode and 0x008 != 0) add(PosixFilePermission.GROUP_EXECUTE)
        if (mode and 0x004 != 0) add(PosixFilePermission.OTHERS_READ)
        if (mode and 0x002 != 0) add(PosixFilePermission.OTHERS_WRITE)
        if (mode and 0x001 != 0) add(PosixFilePermission.OTHERS_EXECUTE)
    }

    private companion object {
        const val HEADER_SIZE = 512
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    }
}
