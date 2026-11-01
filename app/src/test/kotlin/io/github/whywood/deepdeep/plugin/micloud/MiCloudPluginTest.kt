package io.github.whywood.deepdeep.plugin.micloud

import android.content.ContextWrapper
import android.content.res.Resources
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginActionStyle
import io.github.whywood.deepdeep.plugin.PluginStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun statusMappingCoversAllSessionStates() {
        val plugin = plugin()
        assertEquals(
            PluginStatus.NotReady("res:${R.string.plugin_micloud_status_not_logged_in}"),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.SIGNED_OUT, null, null)),
        )
        assertEquals(
            PluginStatus.Ready("小明", "小明"),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.VERIFIED, "小明", 1L)),
        )
        assertEquals(
            PluginStatus.Ready("res:${R.string.plugin_micloud_status_ready_unknown}", null),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.VERIFIED, null, 1L)),
        )
        assertEquals(
            PluginStatus.NotReady("res:${R.string.plugin_micloud_status_expired}"),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.EXPIRED, "小明", 1L)),
        )
        assertEquals(
            PluginStatus.Ready("res:${R.string.plugin_micloud_status_unverified}", "小明"),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.UNVERIFIED, "小明", 1L)),
        )
        assertEquals(
            PluginStatus.NotReady("res:${R.string.plugin_micloud_status_unverifiable}"),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.UNVERIFIED, null, null)),
        )
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
            "登录凭据",
        ).forEach { expected -> assertTrue(expected, skill.markdown.contains(expected)) }
        listOf("serviceToken", "Cookie", "非公开接口", "字段可能变化").forEach { word ->
            assertFalse(word, skill.markdown.contains(word))
        }
    }

    @Test
    fun promptBodyReportsLoginStateEnabledFeaturesAndSafety() {
        val body = miCloudPromptBody(
            PluginStatus.Ready("已登录", "小明"),
            MiCloudCapabilities(notesRead = true, notesWrite = true, smsRead = true),
        )
        assertTrue(body.contains("已登录"))
        assertTrue(body.contains("小明"))
        assertTrue(body.contains("已开启"))
        assertTrue(body.contains("笔记读取"))
        assertTrue(body.contains("笔记写入"))
        assertTrue(body.contains("确认"))
        assertTrue(body.contains("401"))
        listOf("serviceToken", "Cookie", "backend", "engine", "uid", "i.mi.com").forEach { word ->
            assertFalse(word, body.contains(word, ignoreCase = true))
        }
    }

    @Test
    fun promptBodyExplainsSignInWhenNotLoggedIn() {
        val body = miCloudPromptBody(PluginStatus.NotReady("未登录"), MiCloudCapabilities())
        assertTrue(body.contains("不可用"))
        assertTrue(body.contains("plugin_setup"))
        assertTrue(body.contains("登录"))
        assertFalse(body.contains("serviceToken", ignoreCase = true))
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
