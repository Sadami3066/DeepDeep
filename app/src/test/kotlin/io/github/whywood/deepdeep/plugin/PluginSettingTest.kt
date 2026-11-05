package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginSettingTest {
    @Test
    fun textTrimsAndAllowsEmptyValue() {
        val text = PluginSettingSpec.Text("netid", R.string.plugins_title)

        assertEquals("milous", text.normalize("  milous \n"))
        assertEquals("", text.normalize(""))
        assertEquals("张三", text.normalize("张三"))
    }

    @Test
    fun secretKeepsValueVerbatim() {
        val secret = PluginSettingSpec.Text("password", R.string.plugins_title, secret = true)

        assertEquals(" pa ss ", secret.normalize(" pa ss "))
        assertEquals("", secret.normalize(""))
    }
}
