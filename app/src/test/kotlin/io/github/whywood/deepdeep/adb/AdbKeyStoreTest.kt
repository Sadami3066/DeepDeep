package io.github.whywood.deepdeep.adb

import java.io.File
import java.nio.file.Files
import java.security.Signature
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdbKeyStoreTest {
    private lateinit var directory: File
    private lateinit var otherDirectory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("adb-keys").toFile()
        otherDirectory = Files.createTempDirectory("adb-keys-other").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
        otherDirectory.deleteRecursively()
    }

    @Test
    fun generatesAndPersistsCredentials() {
        val credentials = AdbKeyStore(directory).loadOrCreate()
        assertTrue(File(directory, "adb.key").isFile)
        assertTrue(File(directory, "adb.crt").isFile)
        assertEquals("CN=DeepDeep", credentials.certificate.subjectX500Principal.name)
        credentials.certificate.checkValidity()
        assertTrue(signAndVerify(credentials))
    }

    @Test
    fun reloadsTheSameCredentials() {
        val first = AdbKeyStore(directory).loadOrCreate()
        val second = AdbKeyStore(directory).loadOrCreate()
        assertArrayEquals(first.privateKey.encoded, second.privateKey.encoded)
        assertArrayEquals(first.certificate.encoded, second.certificate.encoded)
    }

    @Test
    fun regeneratesWhenKeyFileIsCorrupt() {
        val first = AdbKeyStore(directory).loadOrCreate()
        File(directory, "adb.key").writeBytes("not a key".toByteArray())
        val second = AdbKeyStore(directory).loadOrCreate()
        assertFalse(first.privateKey.encoded.contentEquals(second.privateKey.encoded))
        assertTrue(signAndVerify(second))
    }

    @Test
    fun regeneratesWhenCertificateDoesNotMatchKey() {
        AdbKeyStore(directory).loadOrCreate()
        val other = AdbKeyStore(otherDirectory).loadOrCreate()
        File(directory, "adb.crt").writeBytes(other.certificate.encoded)
        val reloaded = AdbKeyStore(directory).loadOrCreate()
        assertTrue(signAndVerify(reloaded))
        assertNotEquals(other.certificate.publicKey, reloaded.certificate.publicKey)
    }

    private fun signAndVerify(credentials: AdbCredentials): Boolean {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(credentials.privateKey)
        signature.update(PROBE)
        val signed = signature.sign()
        signature.initVerify(credentials.certificate.publicKey)
        signature.update(PROBE)
        return signature.verify(signed)
    }

    private companion object {
        val PROBE = "deepdeep-adb-key".toByteArray()
    }
}
