package io.github.whywood.deepdeep.plugin.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginScopeTest {
    @Test
    fun closeRunsSubscriptionsInReverseOrderAndIsIdempotent() {
        val order = mutableListOf<String>()
        val pluginScope = PluginScope(Dispatchers.Unconfined)
        pluginScope.onClose { order += "first" }
        pluginScope.onClose { order += "second" }

        pluginScope.close()
        pluginScope.close()

        assertEquals(listOf("second", "first"), order)
        assertTrue(pluginScope.isClosed())
    }

    @Test
    fun closeCancelsScopeCoroutines() {
        val pluginScope = PluginScope(Dispatchers.Unconfined)
        val job = pluginScope.scope.launch { delay(Long.MAX_VALUE) }

        pluginScope.close()

        assertFalse(job.isActive)
        assertTrue(job.isCancelled)
    }

    @Test
    fun subscriptionAfterCloseIsRejected() {
        var invoked = false
        val pluginScope = PluginScope(Dispatchers.Unconfined)
        pluginScope.close()

        assertFalse(pluginScope.onClose { invoked = true })
        assertFalse(invoked)
    }

    @Test
    fun closeErrorDoesNotStopOtherSubscriptions() {
        val errors = mutableListOf<Throwable>()
        val order = mutableListOf<String>()
        val pluginScope = PluginScope(Dispatchers.Unconfined, onError = { errors += it })
        pluginScope.onClose {
            order += "failing"
            throw IllegalStateException("boom")
        }
        pluginScope.onClose { order += "healthy" }

        pluginScope.close()

        assertEquals(listOf("healthy", "failing"), order)
        assertEquals(1, errors.size)
        assertEquals("boom", errors.single().message)
    }

    @Test
    fun closingParentJobClosesChildScope() {
        val parent = PluginScope(Dispatchers.Unconfined)
        val child = PluginScope(parent.scope.coroutineContext, "child")
        parent.close()

        assertFalse(child.scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
    }
}
