package io.github.whywood.deepdeep.data

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CleartextTrafficPolicyTest {
    @Test
    fun permitsCleartextForLocalAndLanGateways() {
        val policy = NetworkSecurityPolicy.getInstance()
        listOf("localhost", "127.0.0.1", "192.168.1.2", "10.0.0.5", "172.24.56.208").forEach { host ->
            assertTrue("cleartext traffic to $host must be permitted", policy.isCleartextTrafficPermitted(host))
        }
    }
}
