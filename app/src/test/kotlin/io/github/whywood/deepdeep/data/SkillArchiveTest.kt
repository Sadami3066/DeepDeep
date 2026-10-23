package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillArchiveTest {
    private fun parse(bytes: ByteArray, fallbackName: String = ""): SkillArchiveResult =
        SkillArchive.parse(bytes, fallbackName)

    private fun parsed(bytes: ByteArray, fallbackName: String = ""): ParsedSkill {
        val result = parse(bytes, fallbackName)
        assertTrue("expected success, was $result", result is SkillArchiveResult.Success)
        return (result as SkillArchiveResult.Success).skill
    }

    private fun errorOf(bytes: ByteArray, fallbackName: String = ""): SkillArchiveError {
        val result = parse(bytes, fallbackName)
        assertTrue("expected failure, was $result", result is SkillArchiveResult.Failure)
        return (result as SkillArchiveResult.Failure).error
    }

    @Test
    fun parsesRootSkill() {
        val bytes = SkillTestFixtures.skillZip(
            name = "My Skill",
            description = "Does useful things",
            disableModelInvocation = true,
            body = "Follow these steps.",
            extraEntries = listOf("references/notes.md" to "notes".toByteArray()),
        )
        val skill = parsed(bytes)
        assertEquals("My-Skill", skill.id)
        assertEquals("My Skill", skill.name)
        assertEquals("Does useful things", skill.description)
        assertTrue(skill.disableModelInvocation)
        assertTrue(skill.content.contains("Follow these steps."))
        assertEquals(1, skill.files.size)
        assertEquals("references/notes.md", skill.files[0].path)
        assertEquals("notes", String(skill.files[0].bytes, Charsets.UTF_8))
    }

    @Test
    fun parsesSingleWrapperAndStripsPrefix() {
        val bytes = SkillTestFixtures.skillZip(
            name = "wrapped",
            extraEntries = listOf("scripts/run.sh" to "echo hi".toByteArray()),
            wrapper = "wrapped-skill",
        )
        val skill = parsed(bytes)
        assertEquals("wrapped", skill.id)
        assertEquals(1, skill.files.size)
        assertEquals("scripts/run.sh", skill.files[0].path)
    }

    @Test
    fun ignoresFilesOutsideWrapper() {
        val bytes = SkillTestFixtures.zip(
            listOf(
                "wrapped/SKILL.md" to SkillTestFixtures.skillMarkdown(name = "wrapped", description = "Wrapped skill"),
                "README.md" to "readme".toByteArray(),
            ),
        )
        val skill = parsed(bytes)
        assertTrue(skill.files.isEmpty())
    }

    @Test
    fun usesFallbackNameWhenNameMissing() {
        val bytes = SkillTestFixtures.skillZip(
            name = null,
            description = "Fallback",
            extraEntries = emptyList(),
        )
        val skill = parsed(bytes, fallbackName = "My Imported Skill")
        assertEquals("My-Imported-Skill", skill.id)
        assertEquals("My Imported Skill", skill.name)
    }

    @Test
    fun usesSkillFallbackIdWhenNameUnsafe() {
        val bytes = SkillTestFixtures.skillZip(name = "..", description = "Dots")
        val skill = parsed(bytes)
        assertEquals("skill", skill.id)
    }

    @Test
    fun ignoresMacMetadataAndHiddenEntries() {
        val bytes = SkillTestFixtures.skillZip(
            name = "clean",
            extraEntries = listOf(
                "__MACOSX/._SKILL.md" to "junk".toByteArray(),
                ".DS_Store" to "junk".toByteArray(),
                ".hidden/file.txt" to "hidden".toByteArray(),
            ),
        )
        val skill = parsed(bytes)
        assertTrue(skill.files.isEmpty())
    }

    @Test
    fun rejectsNotZip() {
        assertEquals(SkillArchiveError.NotZip, errorOf(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun rejectsEmptyArchive() {
        assertEquals(SkillArchiveError.Empty, errorOf(SkillTestFixtures.zip(emptyList())))
    }

    @Test
    fun rejectsArchiveWithoutSkill() {
        assertEquals(
            SkillArchiveError.NoSkill,
            errorOf(SkillTestFixtures.zip(listOf("README.md" to "readme".toByteArray()))),
        )
    }

    @Test
    fun rejectsDeeplyNestedSkillOnly() {
        val bytes = SkillTestFixtures.zip(
            listOf("a/b/SKILL.md" to SkillTestFixtures.skillMarkdown(name = "deep", description = "Deep")),
        )
        assertEquals(SkillArchiveError.NoSkill, errorOf(bytes))
    }

    @Test
    fun rejectsMultipleSkills() {
        val bytes = SkillTestFixtures.zip(
            listOf(
                "SKILL.md" to SkillTestFixtures.skillMarkdown(name = "one", description = "One"),
                "other/SKILL.md" to SkillTestFixtures.skillMarkdown(name = "two", description = "Two"),
            ),
        )
        assertEquals(SkillArchiveError.MultipleSkills, errorOf(bytes))
    }

    @Test
    fun rejectsMissingDescription() {
        val bytes = SkillTestFixtures.skillZip(name = "nodesc", description = null)
        assertEquals(SkillArchiveError.MissingDescription, errorOf(bytes))
    }

    @Test
    fun rejectsBlankDescription() {
        val bytes = SkillTestFixtures.skillZip(name = "blank", description = "   ")
        assertEquals(SkillArchiveError.MissingDescription, errorOf(bytes))
    }

    @Test
    fun rejectsTraversalPath() {
        val bytes = SkillTestFixtures.zip(
            listOf(
                "../SKILL.md" to SkillTestFixtures.skillMarkdown(name = "evil", description = "Evil"),
            ),
        )
        assertEquals(SkillArchiveError.UnsafePath, errorOf(bytes))
    }

    @Test
    fun rejectsAbsolutePath() {
        val bytes = SkillTestFixtures.zip(
            listOf(
                "/SKILL.md" to SkillTestFixtures.skillMarkdown(name = "evil", description = "Evil"),
            ),
        )
        assertEquals(SkillArchiveError.UnsafePath, errorOf(bytes))
    }

    @Test
    fun rejectsEncryptedArchive() {
        val encrypted = markEncrypted(
            SkillTestFixtures.skillZip(name = "secret", description = "Secret"),
        )
        assertEquals(SkillArchiveError.NotZip, errorOf(encrypted))
    }

    @Test
    fun rejectsTooManyEntries() {
        val entries = (0 until 1001).map { index -> "file-$index.txt" to "x".toByteArray() }
        assertEquals(SkillArchiveError.TooLarge, errorOf(SkillTestFixtures.zip(entries)))
    }

    @Test
    fun rejectsTooLargeArchive() {
        assertEquals(SkillArchiveError.TooLarge, errorOf(ByteArray(33 * 1024 * 1024)))
    }

    @Test
    fun rejectsTooLargeExtractedContent() {
        val bytes = compressedBomb()
        assertEquals(SkillArchiveError.TooLarge, errorOf(bytes))
    }

    private fun compressedBomb(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("__MACOSX/big.bin"))
            val chunk = ByteArray(64 * 1024)
            repeat(1040) { zip.write(chunk) }
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun markEncrypted(zip: ByteArray): ByteArray {
        val copy = zip.copyOf()
        var end = copy.size - 22
        while (end >= 0) {
            val signature = (copy[end].toInt() and 0xFF) or
                ((copy[end + 1].toInt() and 0xFF) shl 8) or
                ((copy[end + 2].toInt() and 0xFF) shl 16) or
                ((copy[end + 3].toInt() and 0xFF) shl 24)
            if (signature == 0x06054b50) break
            end -= 1
        }
        if (end < 0) return copy
        val centralOffset = (copy[end + 16].toInt() and 0xFF) or
            ((copy[end + 17].toInt() and 0xFF) shl 8) or
            ((copy[end + 18].toInt() and 0xFF) shl 16) or
            ((copy[end + 19].toInt() and 0xFF) shl 24)
        val flagOffset = centralOffset + 8
        copy[flagOffset] = (copy[flagOffset].toInt() or 0x1).toByte()
        return copy
    }
}
