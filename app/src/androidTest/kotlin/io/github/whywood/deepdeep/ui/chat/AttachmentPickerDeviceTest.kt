package io.github.whywood.deepdeep.ui.chat

import android.app.Activity
import android.app.UiAutomation
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.os.ParcelFileDescriptor
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class AttachmentPickerDeviceTest {
    @get:Rule(order = 0)
    val foregroundHost = object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val output = automation.executeShellCommand(
                "am start -W -n ${instrumentation.targetContext.packageName}/androidx.activity.ComponentActivity",
            )
            ParcelFileDescriptor.AutoCloseInputStream(output).use { input ->
                check(!input.readBytes().decodeToString().contains("Error"))
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun cameraResultSurvivesSavedStateRestoration() {
        var requestCode = -1
        var output: Uri? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                code: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                requestCode = code
                @Suppress("DEPRECATION")
                val captured = contract.createIntent(context, input).getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)
                output = captured
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        val received = mutableListOf<Uri>()
        lateinit var pickers: AttachmentPickers
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                pickers = rememberAttachmentPickers { received += it }
            }
        }
        compose.runOnIdle { pickers.takePhoto() }
        assertNotNull(output)
        try {
            restoration.emulateSavedInstanceStateRestore()
            compose.runOnIdle { registry.dispatchResult(requestCode, Activity.RESULT_OK, Intent()) }
            assertEquals(listOf(output), received)
        } finally {
            output?.lastPathSegment?.let { File(context.cacheDir, "camera/$it").delete() }
        }
    }
}
