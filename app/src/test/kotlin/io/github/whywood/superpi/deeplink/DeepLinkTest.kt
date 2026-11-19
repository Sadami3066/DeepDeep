package io.github.whywood.superpi.deeplink

import io.github.whywood.superpi.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test
    fun parsesChatSession() {
        assertEquals(DeepLink.Session("abc-123"), DeepLink.parse("superpi://chat/abc-123"))
        assertEquals(
            DeepLink.Session("0f8fad5b-d9cb-469f-a165-70867728950e"),
            DeepLink.parse("superpi://chat/0f8fad5b-d9cb-469f-a165-70867728950e"),
        )
    }

    @Test
    fun parsesRootPages() {
        val pages = mapOf(
            "superpi://settings" to AppDestination.Settings,
            "superpi://plugins" to AppDestination.Plugins,
            "superpi://mcp" to AppDestination.Mcp,
            "superpi://skills" to AppDestination.Skills,
            "superpi://timers" to AppDestination.Timers,
            "superpi://miniapp" to AppDestination.Miniapp,
        )
        pages.forEach { (raw, destination) ->
            assertEquals(raw, DeepLink.Page(destination), DeepLink.parse(raw))
        }
    }

    @Test
    fun parsesDetailPages() {
        assertEquals(
            DeepLink.Page(AppDestination.PluginPage("io.github.whywood.deepdeep.sdk.foo", "home")),
            DeepLink.parse("superpi://plugins/io.github.whywood.deepdeep.sdk.foo/home"),
        )
        assertEquals(
            DeepLink.Page(AppDestination.MiniappDetail("abc_def-123")),
            DeepLink.parse("superpi://miniapp/abc_def-123"),
        )
    }

    @Test
    fun parsesSettingsSubPages() {
        val pages = mapOf(
            "superpi://settings/access" to AppDestination.SystemAccess,
            "superpi://settings/personalization" to AppDestination.Personalization,
            "superpi://settings/fonts" to AppDestination.Fonts,
            "superpi://settings/assistant" to AppDestination.AssistantSettings,
            "superpi://settings/chat" to AppDestination.ChatSettings,
        )
        pages.forEach { (raw, destination) ->
            assertEquals(raw, DeepLink.Page(destination), DeepLink.parse(raw))
        }
    }

    @Test
    fun parsesPluginCustomPath() {
        assertEquals(
            DeepLink.PluginPath("io.github.whywood.deepdeep.sdk.mail", "thread/123"),
            DeepLink.parse("superpi://plugins/io.github.whywood.deepdeep.sdk.mail/thread/123"),
        )
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"accountId":"a1","threadId":"t1"}""".toByteArray())
        val link = DeepLink.parse("superpi://plugins/demo/thread/123?args=$encoded") as DeepLink.PluginPath
        assertEquals("demo", link.pluginId)
        assertEquals("thread/123", link.path)
        assertEquals("a1", link.arguments["accountId"]?.toString()?.trim('"'))
        assertEquals("t1", link.arguments["threadId"]?.toString()?.trim('"'))
    }

    @Test
    fun rejectsInvalidPluginPaths() {
        listOf(
            "superpi://plugins/demo/Thread/123",
            "superpi://plugins/demo/thread/123?args=not-base64",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun parsesMiniappIdAtLengthLimit() {
        val id = "a".repeat(48)
        assertEquals(DeepLink.Page(AppDestination.MiniappDetail(id)), DeepLink.parse("superpi://miniapp/$id"))
    }

    @Test
    fun allowsTrailingSlash() {
        assertEquals(DeepLink.Page(AppDestination.Settings), DeepLink.parse("superpi://settings/"))
        assertEquals(DeepLink.Page(AppDestination.Plugins), DeepLink.parse("superpi://plugins/"))
        assertEquals(
            DeepLink.Page(AppDestination.PluginPage("io.github.whywood.deepdeep.sdk.foo", "home")),
            DeepLink.parse("superpi://plugins/io.github.whywood.deepdeep.sdk.foo/home/"),
        )
        assertEquals(DeepLink.Session("abc"), DeepLink.parse("superpi://chat/abc/"))
    }

    @Test
    fun trimsRawAndDecodesId() {
        assertEquals(DeepLink.Page(AppDestination.Settings), DeepLink.parse("  superpi://settings  "))
        assertEquals(DeepLink.Session("abc"), DeepLink.parse("superpi://chat/%20abc%20"))
    }

    @Test
    fun acceptsCaseInsensitiveSchemeAndHost() {
        assertEquals(DeepLink.Page(AppDestination.Settings), DeepLink.parse("SUPERPI://Settings"))
    }

    @Test
    fun rejectsOauthCallbacks() {
        assertNull(DeepLink.parse("superpi://oauth"))
        assertNull(DeepLink.parse("superpi://oauth/callback"))
        assertNull(DeepLink.parse("superpi://oauth/callback?code=abc&state=xyz"))
    }

    @Test
    fun rejectsUnknownRoutes() {
        listOf(
            "superpi://unknown",
            "superpi://chats/abc",
            "superpi://providers",
            "superpi://archived",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsOtherSchemes() {
        listOf(
            "http://chat/abc-123",
            "superpis://settings",
            "absuperpi://settings",
            "superpi:settings",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsMalformedInput() {
        listOf(
            "",
            "   ",
            "not a link",
            "superpi://",
            "superpi:///chat/abc",
            "superpi://plugins//",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsChatWithoutValidSessionId() {
        listOf(
            "superpi://chat",
            "superpi://chat/",
            "superpi://chat/abc/def",
            "superpi://chat//abc",
            "superpi://chat/abc%2Fdef",
            "superpi://chat/a%20b",
            "superpi://chat/%00",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsInvalidPluginIds() {
        listOf(
            "superpi://plugins/%20",
            "superpi://plugins/a%20b",
            "superpi://plugins/a%2Fb",
            "superpi://plugins/a",
            "superpi://plugins//feishu",
            "superpi://plugins/a/%20",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsInvalidMiniappIds() {
        listOf(
            "superpi://miniapp/ABC",
            "superpi://miniapp/-abc",
            "superpi://miniapp/a.b",
            "superpi://miniapp/a%20b",
            "superpi://miniapp/" + "a".repeat(49),
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsExtraPathSegments() {
        listOf(
            "superpi://settings/runtime",
            "superpi://mcp/extra",
            "superpi://skills/a/b",
            "superpi://timers/a",
            "superpi://miniapp/a/b",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }
}
