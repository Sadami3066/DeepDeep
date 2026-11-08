package io.github.whywood.deepdeep.deeplink

import io.github.whywood.deepdeep.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test
    fun parsesChatSession() {
        assertEquals(DeepLink.Session("abc-123"), DeepLink.parse("deepdeep://chat/abc-123"))
        assertEquals(
            DeepLink.Session("0f8fad5b-d9cb-469f-a165-70867728950e"),
            DeepLink.parse("deepdeep://chat/0f8fad5b-d9cb-469f-a165-70867728950e"),
        )
    }

    @Test
    fun parsesRootPages() {
        val pages = mapOf(
            "deepdeep://settings" to AppDestination.Settings,
            "deepdeep://plugins" to AppDestination.Plugins,
            "deepdeep://mcp" to AppDestination.Mcp,
            "deepdeep://skills" to AppDestination.Skills,
            "deepdeep://timers" to AppDestination.Timers,
            "deepdeep://miniapp" to AppDestination.Miniapp,
        )
        pages.forEach { (raw, destination) ->
            assertEquals(raw, DeepLink.Page(destination), DeepLink.parse(raw))
        }
    }

    @Test
    fun parsesDetailPages() {
        assertEquals(
            DeepLink.Page(AppDestination.PluginPage("io.github.whywood.deepdeep.sdk.foo", "home")),
            DeepLink.parse("deepdeep://plugins/io.github.whywood.deepdeep.sdk.foo/home"),
        )
        assertEquals(
            DeepLink.Page(AppDestination.MiniappDetail("abc_def-123")),
            DeepLink.parse("deepdeep://miniapp/abc_def-123"),
        )
    }

    @Test
    fun parsesMiniappIdAtLengthLimit() {
        val id = "a".repeat(48)
        assertEquals(DeepLink.Page(AppDestination.MiniappDetail(id)), DeepLink.parse("deepdeep://miniapp/$id"))
    }

    @Test
    fun allowsTrailingSlash() {
        assertEquals(DeepLink.Page(AppDestination.Settings), DeepLink.parse("deepdeep://settings/"))
        assertEquals(DeepLink.Page(AppDestination.Plugins), DeepLink.parse("deepdeep://plugins/"))
        assertEquals(
            DeepLink.Page(AppDestination.PluginPage("io.github.whywood.deepdeep.sdk.foo", "home")),
            DeepLink.parse("deepdeep://plugins/io.github.whywood.deepdeep.sdk.foo/home/"),
        )
        assertEquals(DeepLink.Session("abc"), DeepLink.parse("deepdeep://chat/abc/"))
    }

    @Test
    fun trimsRawAndDecodesId() {
        assertEquals(DeepLink.Page(AppDestination.Settings), DeepLink.parse("  deepdeep://settings  "))
        assertEquals(DeepLink.Session("abc"), DeepLink.parse("deepdeep://chat/%20abc%20"))
    }

    @Test
    fun acceptsCaseInsensitiveSchemeAndHost() {
        assertEquals(DeepLink.Page(AppDestination.Settings), DeepLink.parse("DEEPDEEP://Settings"))
    }

    @Test
    fun rejectsOauthCallbacks() {
        assertNull(DeepLink.parse("deepdeep://oauth"))
        assertNull(DeepLink.parse("deepdeep://oauth/callback"))
        assertNull(DeepLink.parse("deepdeep://oauth/callback?code=abc&state=xyz"))
    }

    @Test
    fun rejectsUnknownRoutes() {
        listOf(
            "deepdeep://unknown",
            "deepdeep://chats/abc",
            "deepdeep://providers",
            "deepdeep://archived",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsOtherSchemes() {
        listOf(
            "http://chat/abc-123",
            "deepdeeps://settings",
            "abdeepdeep://settings",
            "deepdeep:settings",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsMalformedInput() {
        listOf(
            "",
            "   ",
            "not a link",
            "deepdeep://",
            "deepdeep:///chat/abc",
            "deepdeep://plugins//",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsChatWithoutValidSessionId() {
        listOf(
            "deepdeep://chat",
            "deepdeep://chat/",
            "deepdeep://chat/abc/def",
            "deepdeep://chat//abc",
            "deepdeep://chat/abc%2Fdef",
            "deepdeep://chat/a%20b",
            "deepdeep://chat/%00",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsInvalidPluginIds() {
        listOf(
            "deepdeep://plugins/%20",
            "deepdeep://plugins/a%20b",
            "deepdeep://plugins/a%2Fb",
            "deepdeep://plugins/a",
            "deepdeep://plugins/a/b/c",
            "deepdeep://plugins//feishu",
            "deepdeep://plugins/a/%20",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsInvalidMiniappIds() {
        listOf(
            "deepdeep://miniapp/ABC",
            "deepdeep://miniapp/-abc",
            "deepdeep://miniapp/a.b",
            "deepdeep://miniapp/a%20b",
            "deepdeep://miniapp/" + "a".repeat(49),
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }

    @Test
    fun rejectsExtraPathSegments() {
        listOf(
            "deepdeep://settings/runtime",
            "deepdeep://mcp/extra",
            "deepdeep://skills/a/b",
            "deepdeep://timers/a",
            "deepdeep://miniapp/a/b",
        ).forEach { raw -> assertNull(raw, DeepLink.parse(raw)) }
    }
}
