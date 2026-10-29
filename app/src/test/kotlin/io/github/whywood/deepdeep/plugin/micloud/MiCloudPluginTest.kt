package io.github.whywood.deepdeep.plugin.micloud

import android.content.ContextWrapper
import android.content.res.Resources
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginActionStyle
import io.github.whywood.deepdeep.plugin.PluginStatus
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiCloudPluginTest {
    @Test
    fun identityMatchesPluginContract() {
        val plugin = plugin()
        assertEquals("micloud", plugin.id)
        assertEquals("1.0.0", plugin.version)
        assertEquals(DdIcons.Cloud, plugin.icon)
        assertEquals(R.string.plugin_micloud_name, plugin.nameRes)
        assertEquals(R.string.plugin_micloud_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_micloud_size, plugin.sizeLabelRes)
    }

    @Test
    fun signedOutActionsOfferSignIn() {
        val actions = plugin().actions(PluginStatus.NotReady("x"))
        assertEquals(listOf("login"), actions.map { it.id })
        assertEquals(R.string.plugin_micloud_action_login, actions.single().labelRes)
        assertEquals(PluginActionStyle.NORMAL, actions.single().style)
    }

    @Test
    fun signedInActionsOfferReloginAndSignOut() {
        val actions = plugin().actions(PluginStatus.Ready("小明", "小明"))
        assertEquals(listOf("relogin", "logout"), actions.map { it.id })
        assertEquals(R.string.plugin_micloud_action_relogin, actions[0].labelRes)
        assertEquals(R.string.plugin_micloud_action_logout, actions[1].labelRes)
        assertEquals(PluginActionStyle.DESTRUCTIVE, actions[1].style)
    }

    @Test
    fun skillHasFrontmatterAndToolGuidance() {
        val skills = plugin().skills()
        assertEquals(1, skills.size)
        val skill = skills.single()
        assertEquals("deepdeep-micloud", skill.id)
        assertTrue(skill.markdown.startsWith("---\n"))
        val frontmatter = skill.markdown.substringAfter("---\n").substringBefore("\n---")
        assertTrue(frontmatter.contains("name: deepdeep-micloud"))
        assertTrue(frontmatter.contains("description: 小米云数据读写"))
        listOf(
            "mi_cloud_account",
            "mi_cloud_notes",
            "mi_cloud_todo",
            "mi_cloud_sms",
            "mi_cloud_contacts",
            "mi_cloud_gallery",
            "mi_cloud_recordings",
            "重新登录",
            "确认",
            "serviceToken",
        ).forEach { expected -> assertTrue(expected, skill.markdown.contains(expected)) }
    }

    @Test
    fun missingCookieMeansSignedOut() = runTest {
        val state = resolveMiCloudSessionState(
            readCookie = { null },
            profile = { error("profile must not be called") },
        )
        assertEquals(MiCloudSessionState.NotLoggedIn, state)
    }

    @Test
    fun authExceptionMeansExpired() = runTest {
        val state = resolveMiCloudSessionState(
            readCookie = { "serviceToken=token; userId=1" },
            profile = { throw MiCloudAuthException("expired") },
        )
        assertEquals(MiCloudSessionState.Expired, state)
    }

    @Test
    fun otherFailureMeansNetworkError() = runTest {
        val state = resolveMiCloudSessionState(
            readCookie = { "serviceToken=token; userId=1" },
            profile = { throw IOException("HTTP 502") },
        )
        assertEquals(MiCloudSessionState.NetworkError("HTTP 502"), state)
    }

    @Test
    fun validProfileMeansReady() = runTest {
        val state = resolveMiCloudSessionState(
            readCookie = { "serviceToken=token; userId=1" },
            profile = { MiCloudProfile("小明", "1", null) },
        )
        assertEquals(MiCloudSessionState.Ready("小明"), state)
    }

    @Test
    fun enabledFeatureCountCountsReadableFeatures() {
        assertEquals(0, enabledFeatureCount(MiCloudCapabilities()))
        assertEquals(
            3,
            enabledFeatureCount(
                MiCloudCapabilities(notesRead = true, notesWrite = true, todosRead = true, smsRead = true),
            ),
        )
    }

    private fun plugin(): MiCloudPlugin = MiCloudPlugin(FakeContext())

    private class FakeContext : ContextWrapper(null) {
        private val fakeResources = FakeResources()

        override fun getResources(): Resources = fakeResources
    }

    @Suppress("DEPRECATION")
    private class FakeResources : Resources(null, null, null) {
        override fun getString(id: Int): String = "res:$id"
    }
}
