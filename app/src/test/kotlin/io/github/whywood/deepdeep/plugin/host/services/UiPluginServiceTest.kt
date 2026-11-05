package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.PluginServiceUnavailable
import io.github.whywood.deepdeep.plugin.api.ShareRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UiPluginServiceTest {
    @Test
    fun unwiredCallbacksThrowPluginServiceUnavailable() {
        val service = UiPluginService(
            openUrlCallback = null,
            shareCallback = null,
            toastCallback = null,
            openPageCallback = null,
        )

        assertThrows(PluginServiceUnavailable::class.java) { service.openUrl("https://example.com") }
        assertThrows(PluginServiceUnavailable::class.java) { service.toast("hi") }
        assertThrows(PluginServiceUnavailable::class.java) { service.share(ShareRequest(text = "hi")) }
        assertThrows(PluginServiceUnavailable::class.java) { service.openPage("home") }
    }

    @Test
    fun wiredCallbacksReceiveRequests() {
        var openedUrl: String? = null
        var toastMessage: String? = null
        var shared: ShareRequest? = null
        var openedPage: String? = null
        val service = UiPluginService(
            openUrlCallback = { openedUrl = it },
            shareCallback = { shared = it },
            toastCallback = { toastMessage = it },
            openPageCallback = { openedPage = it },
        )

        service.openUrl("https://example.com")
        service.toast("hi")
        service.share(ShareRequest(text = "payload"))
        service.openPage("home")

        assertEquals("https://example.com", openedUrl)
        assertEquals("hi", toastMessage)
        assertEquals("payload", shared?.text)
        assertEquals("home", openedPage)
    }
}
