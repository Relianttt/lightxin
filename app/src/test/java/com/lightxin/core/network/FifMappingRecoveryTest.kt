package com.lightxin.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FifMappingRecoveryTest {
    @Test fun retryRetainsOriginalOperationAndClearsSessionBeforeRequest() = runBlocking {
        val events = mutableListOf<String>()
        val recovery = FifMappingRecovery { events += "reset" }
        var fail = true
        recovery.execute(FifOperation.SUBMIT_HOMEWORK, request = {
            events += "submit:original-text"
            if (fail) Result.failure(FifUserMappingException(FifMappingReason.ACCOUNT_INFO)) else Result.success("ok")
        }) { }
        assertNotNull(recovery.state.value.failure)
        fail = false
        recovery.retry()
        recovery.retry()
        assertEquals(listOf("submit:original-text", "reset", "submit:original-text"), events)
        assertNull(recovery.state.value.failure)
        assertFalse(recovery.state.value.isRetrying)
    }

    @Test fun readFailureAfterSuccessfulWriteDoesNotReplayTheWrite() = runBlocking {
        val recovery = FifMappingRecovery { }
        var writes = 0
        var reads = 0
        recovery.execute(FifOperation.SUBMIT_HOMEWORK, request = { writes++; Result.success(Unit) }) { }
        recovery.execute(FifOperation.WORKS_MORE, request = {
            reads++
            Result.failure<Unit>(FifUserMappingException())
        }) { }
        recovery.retry()
        assertEquals(1, writes)
        assertEquals(2, reads)
    }

    @Test fun duplicateRetryIsIgnoredAndLoadingRemainsUntilCompletion() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var resets = 0
        val recovery = FifMappingRecovery { resets++; entered.complete(Unit); release.await() }
        recovery.execute(FifOperation.QR_SIGN, request = { Result.failure<Unit>(FifUserMappingException()) }) { }
        val job = launch { recovery.retry() }
        entered.await()
        assertTrue(recovery.state.value.isRetrying)
        recovery.retry()
        assertEquals(1, resets)
        release.complete(Unit)
        job.join()
        assertFalse(recovery.state.value.isRetrying)
        assertNotNull(recovery.state.value.failure)
    }

    @Test fun stalePageAndUnrelatedErrorsDoNotShowMappingCard() = runBlocking {
        val recovery = FifMappingRecovery { }
        var current = true
        var calls = 0
        recovery.execute(FifOperation.QUIZ, request = { calls++; Result.failure<Unit>(FifUserMappingException()) },
            isCurrent = { current }) { }
        current = false
        recovery.retry()
        assertEquals(1, calls)
        assertNull(recovery.state.value.failure)
        recovery.execute(FifOperation.HOME, request = { Result.failure<Unit>(Exception("other")) }) { }
        assertNull(recovery.state.value.failure)
    }

    @Test fun concurrentFailuresRemainRecoverableIndividually() = runBlocking {
        val recovery = FifMappingRecovery { }
        var quizFails = true
        recovery.execute(FifOperation.QUIZ, request = {
            if (quizFails) Result.failure<Unit>(FifUserMappingException()) else Result.success(Unit)
        }) { }
        recovery.execute(FifOperation.HOMEWORK, request = { Result.failure<Unit>(FifUserMappingException()) }) { }
        quizFails = false
        recovery.retry()
        assertEquals(FifOperation.HOMEWORK, recovery.state.value.failure?.operation)
    }
}
