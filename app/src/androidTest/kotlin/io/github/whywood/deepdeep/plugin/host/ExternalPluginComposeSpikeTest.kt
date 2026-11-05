package io.github.whywood.deepdeep.plugin.host

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.whywood.deepdeep.plugin.api.NoSlotState
import io.github.whywood.deepdeep.plugin.api.SlotContent
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalPluginComposeSpikeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pluginFile: File = File(
        requireNotNull(context.getExternalFilesDir("plugin-e2e")),
        "plugin-sample.ddp",
    )

    @Ignore("HyperOS aborts activity launch from instrumentation without the MIUI background-popup grant; validate in the real plugin slot UI (P10)")
    @Test
    fun rendersComposeSlotAcrossClassLoader() {
        assertTrue("push the sample bundle first: ${pluginFile.absolutePath}", pluginFile.isFile)
        val loaded = ExternalPluginLoader(context).load(pluginFile)
        val host = stubPluginHost(loaded.manifest, context)
        val binding = loaded.plugin.ui(host).bindings.single { it.slotId == "setup.content" }
        val content = binding.content as SlotContent.Compose
        composeRule.setContent { content.render(NoSlotState) }
        composeRule.onNodeWithText("sample-compose-slot").assertExists()
    }
}
