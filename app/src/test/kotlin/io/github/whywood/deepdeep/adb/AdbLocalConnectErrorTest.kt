package io.github.whywood.deepdeep.adb

import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbLocalConnectErrorTest {
    @Test
    fun authenticationFailureAsksForAuthorization() {
        assertEquals(
            AdbBridgeError.LOCAL_AUTH_REQUIRED,
            localConnectError(AdbAuthenticationFailedException()),
        )
    }

    @Test
    fun pairingRequiredAsksForAuthorization() {
        assertEquals(
            AdbBridgeError.LOCAL_AUTH_REQUIRED,
            localConnectError(AdbPairingRequiredException("pairing required")),
        )
    }

    @Test
    fun socketFailureReportsConnectFailure() {
        assertEquals(
            AdbBridgeError.LOCAL_CONNECT_FAILED,
            localConnectError(IOException("connection refused")),
        )
    }
}
