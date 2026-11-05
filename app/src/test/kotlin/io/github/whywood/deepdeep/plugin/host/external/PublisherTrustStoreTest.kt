package io.github.whywood.deepdeep.plugin.host.external

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PublisherTrustStoreTest {
    private lateinit var dir: File
    private lateinit var file: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("publisher-trust").toFile()
        file = File(dir, "trusted_publishers.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun trustsNewPublisherAndReportsIt() = runBlocking {
        val store = PublisherTrustStore(file)
        val publisher = publisher("ed25519:abc", "key-1")

        val result = store.trust(publisher)

        assertTrue(result is TrustResult.Added)
        assertEquals(publisher, store.trusted(publisher.keyId))
        assertEquals(listOf(publisher), store.publishers())
        assertTrue(file.isFile)
    }

    @Test
    fun trustingSameKeyAndPublicKeyReportsKnown() = runBlocking {
        val store = PublisherTrustStore(file)
        val publisher = publisher("ed25519:abc", "key-1")
        store.trust(publisher)

        val result = store.trust(publisher.copy(name = "renamed"))

        assertTrue(result is TrustResult.Known)
        assertEquals("key-1", (result as TrustResult.Known).publisher.name)
    }

    @Test
    fun conflictingPublicKeyIsRejected() = runBlocking {
        val store = PublisherTrustStore(file)
        val publisher = publisher("ed25519:abc", "key-1")
        store.trust(publisher)

        val result = store.trust(publisher.copy(publicKey = "another-key"))

        assertTrue(result is TrustResult.Conflict)
        assertEquals(publisher, (result as TrustResult.Conflict).existing)
        assertEquals(publisher, store.trusted(publisher.keyId))
    }

    @Test
    fun persistsAcrossInstances() = runBlocking {
        val publisher = publisher("ed25519:abc", "key-1")
        PublisherTrustStore(file).trust(publisher)

        assertEquals(publisher, PublisherTrustStore(file).trusted(publisher.keyId))
    }

    @Test
    fun untrustRemovesPublisher() = runBlocking {
        val store = PublisherTrustStore(file)
        val publisher = publisher("ed25519:abc", "key-1")
        store.trust(publisher)

        assertTrue(store.untrust(publisher.keyId))
        assertNull(store.trusted(publisher.keyId))
        assertFalse(store.untrust(publisher.keyId))
    }

    @Test
    fun publishersAreSortedByKeyId() = runBlocking {
        val store = PublisherTrustStore(file)
        store.trust(publisher("ed25519:zzz", "z"))
        store.trust(publisher("ed25519:aaa", "a"))

        assertEquals(listOf("ed25519:aaa", "ed25519:zzz"), store.publishers().map { it.keyId })
    }

    @Test
    fun corruptFileIsBackedUpAndReset() = runBlocking {
        file.writeText("{broken")
        val store = PublisherTrustStore(file)

        assertTrue(store.publishers().isEmpty())
        val backup = File(dir, "trusted_publishers.json.corrupt")
        assertTrue(backup.isFile)
        assertEquals("{broken", backup.readText())
        assertNotNull(JsonFiles.read(file))
        assertEquals(1, (JsonFiles.read(file)!!["schemaVersion"] as kotlinx.serialization.json.JsonPrimitive).content.toInt())
    }

    @Test
    fun missingFileStartsEmptyWithoutBackup() = runBlocking {
        val store = PublisherTrustStore(file)

        assertTrue(store.publishers().isEmpty())
        assertFalse(File(dir, "trusted_publishers.json.corrupt").exists())
    }

    private fun publisher(keyId: String, name: String): TrustedPublisher =
        TrustedPublisher(keyId, name, "public-key-$keyId", 1234L)
}
