package io.github.whywood.deepdeep.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePairingLinkTest {
    private val instanceId = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val token = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"

    @Test
    fun parsesLanPairingUrl() {
        val link = RemotePairingLink.parse(
            "https://192.168.1.20:3443/mobile-access/pair#instance=$instanceId&token=$token",
        )
        assertNotNull(link)
        assertEquals(instanceId, link!!.instanceId)
        assertEquals(token, link.token)
        assertEquals("https://192.168.1.20:3443", link.origin)
        assertEquals(RemoteEntryKind.Lan, link.kind)
        assertFalse(link.requiresRemoteCa)
    }

    @Test
    fun parsesPublicPairingUrlWithDefaultPort() {
        val link = RemotePairingLink.parse(
            "https://dsh.example.com/mobile-access/pair#instance=$instanceId&token=$token",
        )
        assertNotNull(link)
        assertEquals("https://dsh.example.com", link!!.origin)
        assertEquals(RemoteEntryKind.Public, link.kind)
    }

    @Test
    fun parsesAppKeyFragment() {
        val plain = RemotePairingLink.parse(
            "https://10.0.0.5:3443/mobile-access/pair#key=dsh1.$instanceId.$token",
        )
        assertEquals(RemoteEntryKind.Lan, plain?.kind)
        assertFalse(plain!!.requiresRemoteCa)

        val trusted = RemotePairingLink.parse(
            "https://10.0.0.5:3443/mobile-access/pair#key=dsh2.$instanceId.$token",
        )
        assertTrue(trusted!!.requiresRemoteCa)
    }

    @Test
    fun rejectsMalformedLinks() {
        assertNull(RemotePairingLink.parse(""))
        assertNull(RemotePairingLink.parse("not a url"))
        assertNull(RemotePairingLink.parse("http://192.168.1.20:3443/mobile-access/pair#instance=$instanceId&token=$token"))
        assertNull(RemotePairingLink.parse("https://192.168.1.20:3443/other#instance=$instanceId&token=$token"))
        assertNull(RemotePairingLink.parse("https://192.168.1.20:3443/mobile-access/pair"))
        assertNull(RemotePairingLink.parse("https://192.168.1.20:3443/mobile-access/pair#instance=abc&token=$token"))
        assertNull(RemotePairingLink.parse("https://192.168.1.20:3443/mobile-access/pair#instance=$instanceId&token=short"))
        assertNull(RemotePairingLink.parse("https://192.168.1.20:3443/mobile-access/pair#key=dsh3.$instanceId.$token"))
    }

    @Test
    fun classifiesLanHosts() {
        assertTrue(isLanHost("192.168.1.2"))
        assertTrue(isLanHost("10.0.0.2"))
        assertTrue(isLanHost("172.20.0.2"))
        assertTrue(isLanHost("169.254.10.1"))
        assertTrue(isLanHost("my-pc"))
        assertTrue(isLanHost("my-pc.local"))
        assertTrue(isLanHost("my-pc.lan"))
        assertTrue(isLanHost("127.0.0.1"))
        assertTrue(isLanHost("::1"))
        assertFalse(isLanHost("dsh.example.com"))
        assertFalse(isLanHost("8.8.8.8"))
        assertFalse(isLanHost("172.32.0.1"))
    }
}
