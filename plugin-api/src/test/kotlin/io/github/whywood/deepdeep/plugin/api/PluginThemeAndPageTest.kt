package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginThemeAndPageTest {
    @Test
    fun providesThemesDefaultEmptyAndCountTowardsIsEmpty() {
        val bare = pluginProvides()
        assertTrue(bare.themes.isEmpty())
        assertTrue(bare.isEmpty)

        val themed = pluginProvides(themes = listOf("deepdeep", "santorini"))
        assertEquals(listOf("deepdeep", "santorini"), themed.themes)
        assertFalse(themed.isEmpty)
    }

    @Test
    fun pagePresentationExposesWireValues() {
        assertEquals(PluginPagePresentation.PAGE, PluginPagePresentation.fromWire("page"))
        assertEquals(PluginPagePresentation.SHEET, PluginPagePresentation.fromWire("sheet"))
        assertNull(PluginPagePresentation.fromWire("dialog"))
        assertEquals(2, PluginPagePresentation.ALL.size)
        assertEquals("sheet", PluginPagePresentation.SHEET.wire)
    }

    @Test
    fun pageContextDefaultsToPagePresentation() {
        val context = object : PluginPageContext {
            override val pageId: String = "mail.read"
            override val arguments: JsonObject = JsonObject(emptyMap())
        }
        assertEquals(PluginPagePresentation.PAGE, context.presentation)
    }

    @Test
    fun openPageSheetFallsBackToOpenPageWithArguments() {
        val service = FakePageService()
        val arguments = buildJsonObject { put("messageId", "42") }
        service.openPageSheet("mail.read", arguments)
        assertEquals(listOf("mail.read" to arguments), service.opened)
    }

    @Test
    fun openPageSheetUsesEmptyArgumentsByDefault() {
        val service = FakePageService()
        service.openPageSheet("mail.inbox")
        assertEquals(listOf("mail.inbox" to JsonObject(emptyMap())), service.opened)
    }
}

private fun pluginProvides(
    tools: List<String> = emptyList(),
    skills: List<String> = emptyList(),
    uiSlots: List<String> = emptyList(),
    pages: List<String> = emptyList(),
    themes: List<String> = emptyList(),
): Provides = object : Provides {
    override val tools: List<String> = tools
    override val skills: List<String> = skills
    override val uiSlots: List<String> = uiSlots
    override val pages: List<String> = pages
    override val themes: List<String> = themes
}

private class FakePageService : UiService {
    val opened = mutableListOf<Pair<String, JsonObject>>()

    override fun openPageWithArguments(pageId: String, arguments: JsonObject, newTask: Boolean) {
        opened += pageId to arguments
    }

    override fun openUrl(url: String) = error("unused")

    override fun share(request: ShareRequest) = error("unused")

    override fun shareFiles(paths: List<String>, mimeType: String?) = error("unused")

    override fun toast(message: String) = error("unused")

    override fun openPage(pageId: String) = error("unused")

    override fun closePage(result: JsonObject?) = error("unused")

    override fun pageContext(): PluginPageContext? = null

    override val isDark: Boolean = false

    override suspend fun confirm(request: UiConfirmRequest): Boolean = error("unused")

    override suspend fun prompt(request: UiPromptRequest): String? = error("unused")

    override suspend fun choose(request: UiChooseRequest): String? = error("unused")

    override suspend fun pickDate(request: UiDatePickRequest): Long? = error("unused")

    override suspend fun pickTime(request: UiTimePickRequest): Int? = error("unused")

    override suspend fun pickFiles(request: UiFilePickRequest): List<PluginPickedFile> = error("unused")

    override suspend fun pickImage(request: UiImagePickRequest): PluginPickedFile? = error("unused")

    override suspend fun scanQr(): String? = error("unused")

    override suspend fun pickDirectory(): String? = error("unused")

    override fun snackbar(message: String, actionLabel: String?, action: (() -> Unit)?) = error("unused")
}
