package io.github.whywood.deepdeep.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteConnectionPolicyTest {

    @Test
    fun revokedComputerBlocksWithUnauthorized() {
        assertEquals(
            RemoteGatewayFailure.Unauthorized,
            RemoteReachability.NeedsPairing.blockingFailure(),
        )
    }

    @Test
    fun changedCertificateBlocksWithCertificateRejected() {
        assertEquals(
            RemoteGatewayFailure.CertificateRejected,
            RemoteReachability.CertChanged.blockingFailure(),
        )
    }

    @Test
    fun reachableAndUnknownStatesDoNotBlock() {
        assertNull(RemoteReachability.OnlineLan.blockingFailure())
        assertNull(RemoteReachability.OnlinePublic.blockingFailure())
        assertNull(RemoteReachability.Offline.blockingFailure())
        assertNull(RemoteReachability.Unknown.blockingFailure())
    }
}
