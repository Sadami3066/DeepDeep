package io.github.whywood.deepdeep.plugin.micloud

import android.content.ContextWrapper
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.androidMiCloudManifest
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginActionStyle
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.plugin.host.testPluginHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiCloudPluginTest {
    private val host = testPluginHost(androidMiCloudManifest())

    @Test
    fun manifestMatchesPluginContract() {
        val manifest = androidMiCloudManifest()

        assertEquals("micloud", manifest.id)
        assertEquals("1.0.0", manifest.version)
        assertEquals(IconRef.Resource(DdIcons.Cloud.res), manifest.icon)
        assertEquals(LocalizedText.Resource(R.string.plugin_micloud_name), manifest.name)
        assertEquals(LocalizedText.Resource(R.string.plugin_micloud_summary), manifest.summary)
        assertEquals(LocalizedText.Resource(R.string.plugin_micloud_size), manifest.sizeLabel)
        assertEquals(PluginSource.EXTERNAL, manifest.source)
        assertEquals(
            listOf(
                "mi_cloud_account",
                "mi_cloud_notes",
                "mi_cloud_todo",
                "mi_cloud_sms",
                "mi_cloud_contacts",
                "mi_cloud_gallery",
                "mi_cloud_recordings",
            ),
            manifest.provides.tools,
        )
        assertEquals(listOf("deepdeep-micloud"), manifest.provides.skills)
        assertEquals(MiCloudSettingKeys.ALL, manifest.provides.settings)
    }

    @Test
    fun statusMappingCoversAllSessionStates() {
        val plugin = plugin()
        assertEquals(
            PluginStatus.NotReady(expectedLabel(R.string.plugin_micloud_status_not_logged_in)),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.SIGNED_OUT, null, null), host),
        )
        assertEquals(
            PluginStatus.Ready("小明", "小明"),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.VERIFIED, "小明", 1L), host),
        )
        assertEquals(
            PluginStatus.Ready(expectedLabel(R.string.plugin_micloud_status_ready_unknown), null),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.VERIFIED, null, 1L), host),
        )
        assertEquals(
            PluginStatus.NotReady(expectedLabel(R.string.plugin_micloud_status_expired)),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.EXPIRED, "小明", 1L), host),
        )
        assertEquals(
            PluginStatus.Ready(expectedLabel(R.string.plugin_micloud_status_unverified), "小明"),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.UNVERIFIED, "小明", 1L), host),
        )
        assertEquals(
            PluginStatus.NotReady(expectedLabel(R.string.plugin_micloud_status_unverifiable)),
            plugin.miCloudStatusOf(MiCloudSessionView(MiCloudSessionState.UNVERIFIED, null, null), host),
        )
    }

    @Test
    fun signedOutActionsOfferSignIn() {
        val actions = plugin().actions(host, PluginStatus.NotReady("x"))
        assertEquals(listOf("login"), actions.map { it.id })
        assertEquals(LocalizedText.Resource(R.string.plugin_micloud_action_login), actions.single().label)
        assertEquals(PluginActionStyle.NORMAL, actions.single().style)
    }

    @Test
    fun signedInActionsOfferReloginAndSignOut() {
        val actions = plugin().actions(host, PluginStatus.Ready("小明", "小明"))
        assertEquals(listOf("relogin", "logout"), actions.map { it.id })
        assertEquals(LocalizedText.Resource(R.string.plugin_micloud_action_relogin), actions[0].label)
        assertEquals(LocalizedText.Resource(R.string.plugin_micloud_action_logout), actions[1].label)
        assertEquals(PluginActionStyle.DESTRUCTIVE, actions[1].style)
    }

    @Test
    fun skillHasFrontmatterAndToolGuidance() {
        val skills = plugin().skills(host)
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
    fun settingsExposeSevenCapabilityToggles() {
        val settings = plugin(
            FakeSettings(
                MiCloudCapabilities(
                    notesRead = true,
                    notesWrite = true,
                    todosRead = true,
                    todosWrite = true,
                    smsRead = true,
                ),
            ),
        ).settings()

        assertEquals(MiCloudSettingKeys.ALL, settings.map { it.key })
        assertEquals(
            listOf(
                LocalizedText.Resource(R.string.micloud_feature_notes),
                LocalizedText.Resource(R.string.micloud_feature_todos),
                LocalizedText.Resource(R.string.micloud_feature_sms),
                LocalizedText.Resource(R.string.micloud_feature_contacts),
                LocalizedText.Resource(R.string.micloud_feature_gallery),
                LocalizedText.Resource(R.string.micloud_feature_recordings),
                LocalizedText.Resource(R.string.micloud_feature_account),
            ),
            settings.map { it.title },
        )
        assertEquals(listOf("true", "true", "true", "false", "false", "false", "false"), settings.map { it.default })
        settings.forEach { assertTrue(it is SettingSpec.Toggle) }
    }

    @Test
    fun settingsChangesUpdateCapabilities() = runTest {
        val settings = FakeSettings()
        val plugin = plugin(settings)

        plugin.onSettingsChanged(
            host,
            mapOf(
                MiCloudSettingKeys.NOTES to "true",
                MiCloudSettingKeys.TODOS to "true",
                MiCloudSettingKeys.SMS to "true",
            ),
        )

        val capabilities = settings.capabilities.value
        assertTrue(capabilities.notesRead)
        assertTrue(capabilities.notesWrite)
        assertTrue(capabilities.todosRead)
        assertTrue(capabilities.todosWrite)
        assertTrue(capabilities.smsRead)
        assertFalse(capabilities.contactsRead)
    }

    @Test
    fun promptSectionsFallBackToSignedOutGuidance() {
        val section = plugin().promptSections(host).single()

        assertEquals("micloud", section.id)
        assertTrue(section.body.contains("不可用"))
        assertTrue(section.body.contains("plugin_setup"))
    }

    @Test
    fun detailRowsShowAccountAndFeatureCount() {
        val rows = plugin(
            FakeSettings(MiCloudCapabilities(notesRead = true, notesWrite = true, smsRead = true)),
        ).detailRows(host, PluginStatus.Ready("已登录", "小明"))

        assertEquals(LocalizedText.Resource(R.string.plugin_micloud_detail_account), rows[0].label)
        assertEquals("小明", rows[0].value)
        assertEquals(LocalizedText.Resource(R.string.plugin_micloud_detail_features), rows[1].label)
        assertEquals(expectedLabel(R.string.plugin_micloud_detail_feature_count), rows[1].value)
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

    private fun expectedLabel(id: Int): String = "text:$id"

    private fun plugin(settings: MiCloudSettings = FakeSettings()): MiCloudPlugin =
        MiCloudPlugin(FakeContext(), settings)

    private class FakeSettings(initial: MiCloudCapabilities = MiCloudCapabilities()) : MiCloudSettings {
        private val state = MutableStateFlow(initial)

        override val capabilities: StateFlow<MiCloudCapabilities> = state

        override fun snapshot(): MiCloudCapabilities = state.value

        override suspend fun setRead(feature: MiCloudFeature, enabled: Boolean) {
            state.value = state.value.withRead(feature, enabled)
        }

        override suspend fun setWrite(feature: MiCloudFeature, enabled: Boolean) {
            state.value = state.value.withWrite(feature, enabled)
        }

        override suspend fun clear() {
            state.value = MiCloudCapabilities()
        }
    }

    private class FakeContext : ContextWrapper(null)
}
