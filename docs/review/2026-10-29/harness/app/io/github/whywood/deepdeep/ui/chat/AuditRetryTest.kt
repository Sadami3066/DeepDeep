package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Reuses the repository's private fake fixtures through reflection; calls the real ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditRetryTest {
    @Test fun stopDuringRetryDelayStillRetries() {
        val fixture = ChatViewModelTest()
        val dispatcher = ChatViewModelTest::class.java.getDeclaredField("dispatcher").apply { isAccessible=true }.get(fixture) as TestDispatcher
        fun instance(suffix: String): Any = Class.forName("io.github.whywood.deepdeep.ui.chat.ChatViewModelTest\$$suffix")
            .getDeclaredConstructor().apply { isAccessible=true }.newInstance()
        val fake = instance("FakeRuntime")
        val settings = instance("FakeSettings")
        val helper = ChatViewModelTest::class.java.declaredMethods.single { it.name == "viewModel" }.apply { isAccessible=true }
        fixture.setUp()
        try {
            runTest(dispatcher) {
                val viewModel = helper.invoke(fixture,fake,true,null,settings) as ChatViewModel
                advanceUntilIdle()
                viewModel.onInputChange("audit")
                viewModel.send()
                runCurrent()
                val runtime = fake as AgentRuntime
                val events = runtime.events as MutableSharedFlow<SessionRuntimeEvent>
                val id = viewModel.state.value.conversationId!!
                events.emit(SessionRuntimeEvent(id,RuntimeEvent.RunStarted("audit-run",1)))
                events.emit(SessionRuntimeEvent(id,RuntimeEvent.RunFailed("audit-run","network error")))
                runCurrent()
                assertTrue(viewModel.state.value.sending)
                viewModel.stop()
                advanceTimeBy(2500)
                runCurrent()
                val retried = fake.javaClass.getDeclaredMethod("getRetried").apply { isAccessible=true }.invoke(fake) as Int
                assertEquals(1,retried)
                println("AUDIT stop during retry delay still caused runtime retry count=$retried")
            }
        } finally { fixture.tearDown() }
    }
}
