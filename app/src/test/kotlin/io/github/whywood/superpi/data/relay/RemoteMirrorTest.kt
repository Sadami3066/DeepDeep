package io.github.whywood.superpi.data.relay

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteMirrorTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("relay-mirror").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun projectsUserAndStreamingAssistantText() {
        assertEquals(
            RemoteMirrorLine(role = "user", text = "hi", tool = null, at = 1L),
            RemoteMirrorProjection.user("hi", 1L),
        )
        assertEquals(
            RemoteMirrorLine(role = "assistant", text = "hello", tool = null, at = 2L),
            RemoteMirrorProjection.project(RemoteAgentEvent.TextDelta("hello"), 2L),
        )
    }

    @Test
    fun projectsToolCallAndResult() {
        assertEquals(
            RemoteMirrorLine(role = "tool", text = "{\"command\":\"ls\"}", tool = "shell", at = 3L),
            RemoteMirrorProjection.project(
                RemoteAgentEvent.ToolCall("call-1", "shell", "{\"command\":\"ls\"}"),
                3L,
            ),
        )
        assertEquals(
            RemoteMirrorLine(role = "tool", text = "done", tool = "call-1", at = 4L),
            RemoteMirrorProjection.project(RemoteAgentEvent.ToolResult("call-1", "done", isError = false), 4L),
        )
    }

    @Test
    fun projectsFailuresAndSkipsEphemeralEvents() {
        assertEquals(
            RemoteMirrorLine(role = "error", text = "boom", tool = null, at = 5L),
            RemoteMirrorProjection.project(RemoteAgentEvent.Failure("boom"), 5L),
        )
        assertNull(RemoteMirrorProjection.project(RemoteAgentEvent.AgentStart, 5L))
        assertNull(RemoteMirrorProjection.project(RemoteAgentEvent.ReasoningDelta("hm"), 5L))
        assertNull(RemoteMirrorProjection.project(RemoteAgentEvent.AgentEnd, 5L))
        assertNull(RemoteMirrorProjection.project(RemoteAgentEvent.AgentSettled, 5L))
    }

    @Test
    fun encodesRoleTextToolAndAtFields() {
        val encoded = RemoteMirrorProjection.toJsonLine(
            RemoteMirrorLine(role = "assistant", text = "line\nwith newline", tool = null, at = 42L),
        )
        val json = Json.parseToJsonElement(encoded).jsonObject

        assertEquals("assistant", json["role"]?.jsonPrimitive?.content)
        assertEquals("line\nwith newline", json["text"]?.jsonPrimitive?.content)
        assertEquals(42L, json["at"]?.jsonPrimitive?.long)
        assertTrue(json.containsKey("tool"))
        assertEquals("null", json["tool"].toString())
    }

    @Test
    fun appendsJsonlInsidePrivateRoot() {
        val store = RemoteMirrorStore(File(root, "mirror"))
        val sessionId = "session-1"

        assertTrue(store.append("device-a", "bot-a", sessionId, RemoteMirrorProjection.user("one", 1L)))
        assertTrue(
            store.append(
                "device-a",
                "bot-a",
                sessionId,
                RemoteMirrorProjection.project(RemoteAgentEvent.TextDelta("two"), 2L)!!,
            ),
        )

        val file = File(File(File(File(root, "mirror"), "device-a"), "bot-a"), "session-1.jsonl")
        assertTrue(file.isFile)
        val lines = file.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"role\":\"user\""))
        assertTrue(lines[1].contains("\"role\":\"assistant\""))
    }

    @Test
    fun sanitizesPathSegmentsAndStaysInsideRoot() {
        val store = RemoteMirrorStore(root)
        val line = RemoteMirrorProjection.user("hi", 1L)

        assertTrue(store.append("../evil", "bot/../id", "session", line))

        val files = root.walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, files.size)
        assertTrue(files.single().canonicalPath.startsWith(root.canonicalPath + File.separator))
        assertTrue(files.single().name.endsWith(".jsonl"))
        assertFalse(files.single().name.contains(".."))
    }

    @Test
    fun normalizesSegmentsForFileNames() {
        assertEquals("device-a_1", mirrorPathSegment("device-a_1"))
        assertEquals("___evil", mirrorPathSegment("../evil"))
        assertEquals("__", mirrorPathSegment("设备"))
        assertEquals("session", mirrorPathSegment(""))
        assertEquals(64, mirrorPathSegment("a".repeat(80)).length)
    }

    @Test
    fun reportsFailureWhenRootIsNotWritable() {
        root.deleteRecursively()
        root.writeText("not a directory")
        val store = RemoteMirrorStore(File(root, "mirror"))

        assertFalse(store.append("device", "bot", "session", RemoteMirrorProjection.user("hi", 1L)))
    }
}
