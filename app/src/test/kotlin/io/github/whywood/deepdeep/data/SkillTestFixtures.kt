package io.github.whywood.deepdeep.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object SkillTestFixtures {
    fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun skillMarkdown(
        name: String? = null,
        description: String? = "Test skill",
        disableModelInvocation: Boolean = false,
        body: String = "Instructions",
    ): ByteArray {
        val builder = StringBuilder()
        builder.append("---\n")
        if (name != null) builder.append("name: ").append(name).append('\n')
        if (description != null) builder.append("description: ").append(description).append('\n')
        if (disableModelInvocation) builder.append("disable-model-invocation: true\n")
        builder.append("---\n\n").append(body).append('\n')
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    fun skillZip(
        name: String? = "alpha",
        description: String? = "Alpha skill",
        disableModelInvocation: Boolean = false,
        body: String = "Alpha body",
        extraEntries: List<Pair<String, ByteArray>> = emptyList(),
        wrapper: String? = null,
    ): ByteArray {
        val prefix = wrapper?.let { "$it/" } ?: ""
        val entries = buildList {
            add(prefix + "SKILL.md" to skillMarkdown(name, description, disableModelInvocation, body))
            extraEntries.forEach { (path, bytes) -> add(prefix + path to bytes) }
        }
        return zip(entries)
    }
}
