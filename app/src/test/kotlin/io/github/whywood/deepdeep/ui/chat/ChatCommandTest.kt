package io.github.whywood.deepdeep.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCommandTest {
    @Test
    fun plainTextIsNotACommand() {
        assertEquals(ParsedCommand.None, parseChatCommand("hello"))
        assertEquals(ParsedCommand.None, parseChatCommand(" /compact"))
        assertEquals(ParsedCommand.Unknown(""), parseChatCommand("/"))
        assertEquals(ParsedCommand.None, parseChatCommand("/Compact"))
    }

    @Test
    fun parsesKnownCommands() {
        assertEquals(ParsedCommand.Found(ChatCommand.COMPACT, ""), parseChatCommand("/compact"))
        assertEquals(ParsedCommand.Found(ChatCommand.GOAL, ""), parseChatCommand("/goal"))
    }

    @Test
    fun parsesArguments() {
        assertEquals(
            ParsedCommand.Found(ChatCommand.GOAL, "finish the report"),
            parseChatCommand("/goal finish the report"),
        )
        assertEquals(ParsedCommand.Found(ChatCommand.GOAL, "pause"), parseChatCommand("/goal   pause "))
    }

    @Test
    fun unknownCommandCarriesName() {
        val parsed = parseChatCommand("/summarize now")
        assertEquals(ParsedCommand.Unknown("summarize"), parsed)
    }

    @Test
    fun commandQueryStopsAtWhitespace() {
        assertNull(commandQuery("hello"))
        assertEquals("compact", commandQuery("/compact"))
        assertEquals("go", commandQuery("/go"))
        assertNull(commandQuery("/goal x"))
    }

    @Test
    fun triggerDetectsSlashAtWordBoundary() {
        assertEquals(CommandTrigger(0, "co"), findCommandTrigger("/co"))
        assertEquals(CommandTrigger(3, "co"), findCommandTrigger("hi /co"))
        assertEquals(CommandTrigger(5, ""), findCommandTrigger("text /"))
        assertNull(findCommandTrigger("hi/co"))
        assertNull(findCommandTrigger("hello"))
        assertNull(findCommandTrigger("hello "))
    }

    @Test
    fun formatsTokens() {
        assertEquals("999", formatTokenCount(999))
        assertEquals("1.0K", formatTokenCount(1000))
        assertEquals("9.9K", formatTokenCount(9900))
        assertEquals("12K", formatTokenCount(12_300))
        assertEquals("1.2M", formatTokenCount(1_200_000))
        assertTrue(formatTokenCount(1_200_000).endsWith("M"))
    }
}
