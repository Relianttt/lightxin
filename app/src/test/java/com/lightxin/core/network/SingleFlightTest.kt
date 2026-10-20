package com.lightxin.core.network

import com.lightxin.core.auth.AccountSession
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SingleFlightTest {
    @Test fun cancellingOneAwaiterDoesNotCancelSharedWork() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Int>()
        val calls = AtomicInteger()
        val flights = SingleFlight<String, Int>()
        val first = async(start = CoroutineStart.UNDISPATCHED) { flights.run("a", owner) { calls.incrementAndGet(); gate.await() } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { flights.run("a", owner) { calls.incrementAndGet(); 9 } }
        first.cancelAndJoin()
        gate.complete(7)
        assertEquals(7, second.await())
        assertEquals(1, calls.get())
        owner.cancel()
    }

    @Test fun invalidatingAccountCancelsOldOwnerAndRejectsLatePublication() = runBlocking {
        val account = AccountSession()
        val old = account.capture()
        val started = CompletableDeferred<Unit>()
        val task = async { SingleFlight<String, Int>().run("a", old.scope) { started.complete(Unit); awaitCancellation() } }
        started.await()
        account.invalidate()
        assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
        assertFalse(account.publish(old) { fail("Old account published") })
        assertTrue(account.publish(account.capture()) {})
        account.invalidate()
    }

    @Test fun failedRequestIsRemovedAndCanBeRetried() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val flights = SingleFlight<String, Int>()
        assertTrue(runCatching { flights.run("a", owner) { error("offline") } }.isFailure)
        assertEquals(3, flights.run("a", owner) { 3 })
        owner.cancel()
    }
}
