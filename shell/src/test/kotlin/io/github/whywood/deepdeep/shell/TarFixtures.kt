package io.github.whywood.deepdeep.shell

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants

internal object TarFixtures {
    fun archive(entries: List<Entry>): ByteArray = ByteArrayOutputStream().also { bytes ->
        GZIPOutputStream(bytes).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                entries.forEach { entry ->
                    when (entry) {
                        is Entry.Directory -> {
                            val tarEntry = TarArchiveEntry(entry.name)
                            tarEntry.mode = entry.mode
                            tar.putArchiveEntry(tarEntry)
                            tar.closeArchiveEntry()
                        }

                        is Entry.File -> {
                            val tarEntry = TarArchiveEntry(entry.name)
                            tarEntry.mode = entry.mode
                            tarEntry.size = entry.content.size.toLong()
                            tar.putArchiveEntry(tarEntry)
                            tar.write(entry.content)
                            tar.closeArchiveEntry()
                        }

                        is Entry.Symlink -> {
                            val tarEntry = TarArchiveEntry(entry.name, TarConstants.LF_SYMLINK)
                            tarEntry.linkName = entry.target
                            tar.putArchiveEntry(tarEntry)
                            tar.closeArchiveEntry()
                        }

                        is Entry.Hardlink -> {
                            val tarEntry = TarArchiveEntry(entry.name, TarConstants.LF_LINK)
                            tarEntry.linkName = entry.target
                            tar.putArchiveEntry(tarEntry)
                            tar.closeArchiveEntry()
                        }
                    }
                }
            }
        }
    }.toByteArray()

    sealed interface Entry {
        val name: String

        data class Directory(override val name: String, val mode: Int = 0x1ED) : Entry

        data class File(
            override val name: String,
            val content: ByteArray,
            val mode: Int = 0x1A4,
        ) : Entry

        data class Symlink(override val name: String, val target: String) : Entry

        data class Hardlink(override val name: String, val target: String) : Entry
    }
}
