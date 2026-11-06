package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.ui.common.chat.formatTokenCount

import io.github.whywood.deepdeep.ui.common.chat.findCommandTrigger

import io.github.whywood.deepdeep.ui.common.chat.CommandTrigger

import io.github.whywood.deepdeep.ui.common.chat.commandQuery

import io.github.whywood.deepdeep.ui.common.chat.extractSkillMentions

import io.github.whywood.deepdeep.ui.common.chat.SkillMentions

import io.github.whywood.deepdeep.ui.common.chat.ChatCommand

import io.github.whywood.deepdeep.ui.common.chat.parseChatCommand

import io.github.whywood.deepdeep.ui.common.chat.ParsedCommand

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
    fun parsesSkillNamespace() {
        assertEquals(ParsedCommand.Skill("pdf-tools", ""), parseChatCommand("/skill:pdf-tools"))
        assertEquals(
            ParsedCommand.Skill("pdf-tools", "make a report"),
            parseChatCommand("/skill:pdf-tools make a report"),
        )
        assertEquals(ParsedCommand.Skill("a1_b-c", "x"), parseChatCommand("/skill:a1_b-c x"))
        assertEquals(ParsedCommand.Skill("2fa", ""), parseChatCommand("/skill:2fa"))
    }

    @Test
    fun invalidSkillNamesAreUnknown() {
        assertEquals(ParsedCommand.Unknown("skill:"), parseChatCommand("/skill:"))
        assertEquals(ParsedCommand.Unknown("skill:Foo"), parseChatCommand("/skill:Foo args"))
        assertEquals(ParsedCommand.Unknown("skill:bad.name"), parseChatCommand("/skill:bad.name x"))
        val tooLong = "a".repeat(49)
        assertEquals(ParsedCommand.Unknown("skill:$tooLong"), parseChatCommand("/skill:$tooLong"))
        assertEquals(ParsedCommand.None, parseChatCommand("/Skill:foo"))
    }

    @Test
    fun skillNamespaceDiffersFromPlainCommand() {
        assertEquals(ParsedCommand.Unknown("skill"), parseChatCommand("/skill foo"))
        assertEquals(ParsedCommand.Skill("nope", ""), parseChatCommand("/skill:nope"))
    }

    @Test
    fun miniappIsLoadedThroughSkillNamespace() {
        assertEquals(ParsedCommand.Skill("miniapp", ""), parseChatCommand("/skill:miniapp"))
        assertEquals(
            ParsedCommand.Skill("miniapp", "build a todo app"),
            parseChatCommand("/skill:miniapp build a todo app"),
        )
        assertEquals(ParsedCommand.Unknown("miniapp-skill"), parseChatCommand("/miniapp-skill build"))
    }

    @Test
    fun extractsSkillMentionsFromAnyPosition() {
        assertEquals(
            SkillMentions(listOf("miniapp"), "使用一下这个skill"),
            extractSkillMentions("使用一下这个skill /skill:miniapp"),
        )
        assertEquals(
            SkillMentions(listOf("miniapp"), "用这个做"),
            extractSkillMentions("/skill:miniapp 用这个做"),
        )
        assertEquals(
            SkillMentions(listOf("pdf-tools"), "看一下"),
            extractSkillMentions("看一下 /skill:pdf-tools"),
        )
    }

    @Test
    fun extractsMultipleSkillMentionsAndDeduplicates() {
        assertEquals(
            SkillMentions(listOf("miniapp", "pdf-tools"), "先做 再导出"),
            extractSkillMentions("/skill:miniapp 先做 /skill:pdf-tools 再导出"),
        )
        assertEquals(
            SkillMentions(listOf("miniapp"), "两次"),
            extractSkillMentions("/skill:miniapp 两次 /skill:miniapp"),
        )
    }

    @Test
    fun ignoresSkillMentionsThatAreNotStandaloneTokens() {
        assertEquals(null, extractSkillMentions("x/skill:miniapp"))
        assertEquals(null, extractSkillMentions("/skill:Miniapp"))
        assertEquals(null, extractSkillMentions("/skill:"))
        assertEquals(null, extractSkillMentions("plain"))
    }

    @Test
    fun ignoresSkillMentionsInsideCode() {
        assertEquals(null, extractSkillMentions("`/skill:miniapp`"))
        assertEquals(null, extractSkillMentions("```\n/skill:miniapp\n```"))
        assertEquals(
            SkillMentions(listOf("miniapp"), "```\n/skill:miniapp\n```\n看代码"),
            extractSkillMentions("```\n/skill:miniapp\n```\n看代码 /skill:miniapp"),
        )
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
