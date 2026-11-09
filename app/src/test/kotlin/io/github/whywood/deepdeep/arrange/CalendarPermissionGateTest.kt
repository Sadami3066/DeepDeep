package io.github.whywood.deepdeep.arrange

import android.Manifest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalendarPermissionGateTest {
    private val read = Manifest.permission.READ_CALENDAR

    @Test
    fun grantedPermissionDoesNotRequest() = runBlocking {
        val requests = AtomicInteger()
        val gate = CalendarPermissionGate(
            check = { true },
            request = { requests.incrementAndGet(); true },
        )
        gate.ensure(read)
        assertEquals(0, requests.get())
    }

    @Test
    fun missingPermissionRequestsOnce() = runBlocking {
        var granted = false
        val requests = AtomicInteger()
        val gate = CalendarPermissionGate(
            check = { granted },
            request = { requests.incrementAndGet(); granted = true; true },
        )
        gate.ensure(read)
        gate.ensure(read)
        assertEquals(1, requests.get())
    }

    @Test
    fun deniedPermissionThrowsWithoutRequestingAgain() = runBlocking {
        val requests = AtomicInteger()
        val gate = CalendarPermissionGate(
            check = { false },
            request = { requests.incrementAndGet(); false },
        )
        assertThrows(ArrangeCalendarPermissionException::class.java) {
            runBlocking { gate.ensure(read) }
        }
        assertThrows(ArrangeCalendarPermissionException::class.java) {
            runBlocking { gate.ensure(read) }
        }
        assertEquals(1, requests.get())
    }

    @Test
    fun concurrentRequestsShareOnePrompt() = runBlocking {
        var granted = false
        val requests = AtomicInteger()
        val gate = CalendarPermissionGate(
            check = { granted },
            request = { requests.incrementAndGet(); delay(50); granted = true; true },
        )
        val first = async { gate.ensure(read) }
        val second = async { gate.ensure(read) }
        first.await()
        second.await()
        assertEquals(1, requests.get())
    }
}
