package io.github.whywood.deepdeep.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbLocalEndpointTest {
    @Test
    fun parsesDefaultEndpoint() {
        assertEquals(
            AdbLocalEndpoint("127.0.0.1", 5555),
            AdbLocalEndpoint.parse("127.0.0.1", "5555"),
        )
    }

    @Test
    fun trimsWhitespace() {
        assertEquals(
            AdbLocalEndpoint("192.168.1.5", 5037),
            AdbLocalEndpoint.parse(" 192.168.1.5 ", " 5037 "),
        )
    }

    @Test
    fun rejectsBlankHost() {
        assertNull(AdbLocalEndpoint.parse("", "5555"))
        assertNull(AdbLocalEndpoint.parse("   ", "5555"))
    }

    @Test
    fun rejectsHostWithWhitespace() {
        assertNull(AdbLocalEndpoint.parse("127.0.0.1 5555", "5555"))
    }

    @Test
    fun rejectsInvalidPort() {
        assertNull(AdbLocalEndpoint.parse("127.0.0.1", "0"))
        assertNull(AdbLocalEndpoint.parse("127.0.0.1", "65536"))
        assertNull(AdbLocalEndpoint.parse("127.0.0.1", "abc"))
        assertNull(AdbLocalEndpoint.parse("127.0.0.1", ""))
    }
}
