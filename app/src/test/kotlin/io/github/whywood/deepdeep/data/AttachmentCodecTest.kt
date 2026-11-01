package io.github.whywood.deepdeep.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentCodecTest {
    private fun ref(name: String, mime: String, path: String, size: Long = 1024L): AttachmentRef = AttachmentRef(
        id = path,
        name = name,
        mime = mime,
        size = size,
        path = path,
        kind = attachmentKind(mime, name),
    )

    @Test
    fun encodesAndParsesAttachmentBlock() {
        val attachments = listOf(
            ref("设计稿.png", "image/png", "attachments/abc_设计稿.png", 2048L),
            ref("notes.md", "text/markdown", "attachments/def_notes.md", 512L),
        )
        val encoded = encodeAttachmentBlock(attachments, File("/data/home"))
        assertTrue(encoded.startsWith("<attachments>"))
        assertTrue(encoded.endsWith("</attachments>"))
        val parsed = parseAttachmentBlock("$encoded\n\n正文内容")
        assertEquals(2, parsed.attachments.size)
        assertEquals("/data/home/attachments/abc_设计稿.png", parsed.attachments[0].path)
        assertEquals("设计稿.png", parsed.attachments[0].name)
        assertEquals(AttachmentKind.IMAGE, parsed.attachments[0].kind)
        assertEquals("notes.md", parsed.attachments[1].name)
        assertEquals(AttachmentKind.TEXT, parsed.attachments[1].kind)
        assertEquals("正文内容", parsed.text)
    }

    @Test
    fun absoluteAttachmentPathsAreNotPrefixedWithWorkspace() {
        val attachment = ref("note.md", "text/markdown", "/data/cache/note.md")
        val encoded = encodeAttachmentBlock(listOf(attachment), File("/data/home"))

        assertEquals("/data/cache/note.md", parseAttachmentBlock(encoded).attachments.single().path)
        assertEquals(File("/data/cache/note.md"), attachmentFile(attachment, File("/data/home")))
    }

    @Test
    fun legacyRelativeAttachmentsResolveAgainstTheirWorkspace() {
        val attachment = ref("note.md", "text/markdown", "attachments/note.md")
        assertEquals(File("/data/home/attachments/note.md"), attachmentFile(attachment, File("/data/home")))
    }

    @Test
    fun parsesRealPromptWithHintLine() {
        val text = "<attachments>\n" +
            "- attachments/a595bcc61759_design.png | design.png | image/png | 11334\n" +
            "files are in the workspace; use the read tool to inspect them\n" +
            "</attachments>\n\nwhats"
        val parsed = parseAttachmentBlock(text)
        assertEquals(1, parsed.attachments.size)
        assertEquals("design.png", parsed.attachments[0].name)
        assertEquals(AttachmentKind.IMAGE, parsed.attachments[0].kind)
        assertEquals("whats", parsed.text)
    }

    @Test
    fun plainTextWithoutBlockReturnsNoAttachments() {
        val parsed = parseAttachmentBlock("只有文本")
        assertEquals(0, parsed.attachments.size)
        assertEquals("只有文本", parsed.text)
    }

    @Test
    fun classifiesByExtensionWhenMimeIsGeneric() {
        assertEquals(AttachmentKind.IMAGE, attachmentKind("application/octet-stream", "photo.jpeg"))
        assertEquals(AttachmentKind.TEXT, attachmentKind("application/octet-stream", "main.kt"))
        assertEquals(AttachmentKind.OTHER, attachmentKind("application/octet-stream", "archive.zip"))
    }

    @Test
    fun formatsFileSize() {
        assertEquals("512 B", formatAttachmentSize(512))
        assertEquals("2 KB", formatAttachmentSize(2048))
        assertEquals("1.5 MB", formatAttachmentSize(1572864))
    }

    @Test
    fun sanitizesNames() {
        assertEquals("a_b_c.md", sanitizeAttachmentName("a/b:c.md"))
        assertEquals("attachment", sanitizeAttachmentName("   "))
    }
}
