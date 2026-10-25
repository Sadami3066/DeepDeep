package io.github.whywood.deepdeep.plugin.micloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiCloudWebWriterTest {
    @Test
    fun resolvesRelativeIMiPathsToHttps() {
        assertEquals("https://i.mi.com/note/full/page/?ts=1", miCloudUrl("/note/full/page/?ts=1"))
        assertEquals("https://i.mi.com", miCloudUrl("https://i.mi.com"))
        assertEquals("https://i.mi.com/x", miCloudUrl(" https://i.mi.com/x "))
    }

    @Test
    fun rejectsForeignAndBrokenTargets() {
        assertNull(miCloudUrl(""))
        assertNull(miCloudUrl("   "))
        assertNull(miCloudUrl("https://evil.example/x"))
        assertNull(miCloudUrl("https://i.mi.com.evil.example/x"))
        assertNull(miCloudUrl("http://i.mi.com/x"))
        assertNull(miCloudUrl("i.mi.com/x"))
        assertNull(miCloudUrl("/path with space"))
    }

    @Test
    fun extractsCookieValues() {
        val cookie = "a=1; serviceToken=abc123; userId=42; empty="
        assertEquals("abc123", cookieValue(cookie, "serviceToken"))
        assertEquals("42", cookieValue(cookie, "userId"))
        assertNull(cookieValue(cookie, "missing"))
        assertNull(cookieValue(null, "serviceToken"))
        assertNull(cookieValue("serviceToken=", "serviceToken"))
    }

    @Test
    fun encodesFormBodies() {
        assertEquals("a=x+y&b=1", encodeForm(mapOf("a" to "x y", "b" to "1")))
        assertEquals("entry=%7B%22subject%22%3A%22%E6%A0%87%E9%A2%98%22%7D", encodeForm(
            mapOf("entry" to "{\"subject\":\"标题\"}"),
        ))
    }

    @Test
    fun detectsAuthFailures() {
        assertTrue(isAuthFailure(401, ""))
        assertTrue(isAuthFailure(403, ""))
        assertTrue(isAuthFailure(200, """{"R":"401","S":"Err"}"""))
        assertTrue(isAuthFailure(200, """{"code":401}"""))
        assertFalse(isAuthFailure(200, """{"R":"0","data":{}}"""))
        assertFalse(isAuthFailure(200, "not-json"))
        assertFalse(isAuthFailure(500, """{"code":10016}"""))
    }
}
