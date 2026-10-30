package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.runtime.testing.FakeAgentRuntime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class UsageContractTest {
    @Test
    fun runtimesWithoutUsageSupportReportNoUsage() = runTest {
        val runtime = FakeAgentRuntime()
        assertNull(runtime.usage("session-1"))
        assertSame(CompactionOutcome.Unavailable, runtime.compact("session-1"))
    }
}
