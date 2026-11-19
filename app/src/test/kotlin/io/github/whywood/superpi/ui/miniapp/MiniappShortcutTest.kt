package io.github.whywood.superpi.ui.miniapp

import io.github.whywood.superpi.deeplink.DeepLink
import io.github.whywood.superpi.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniappShortcutTest {
    @Test
    fun shortcutIdIsScopedToMiniapp() {
        assertEquals("miniapp_demo", MiniappShortcut.shortcutId("demo"))
        assertEquals("miniapp_app-mw5kzkd2", MiniappShortcut.shortcutId("app-mw5kzkd2"))
    }

    @Test
    fun deepLinkOpensMiniappDetail() {
        val link = DeepLink.parse(MiniappShortcut.deepLink("demo"))

        assertEquals(DeepLink.Page(AppDestination.MiniappDetail("demo")), link)
    }
}
