package com.lightxin.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Cancelling an awaiter does not cancel the shared owner's work. */
class SingleFlight<K, V> {
    private val requests = mutableMapOf<K, Deferred<V>>()

    suspend fun run(key: K, scope: CoroutineScope, request: suspend () -> V): V {
        val task = synchronized(requests) {
            requests[key] ?: scope.async(start = CoroutineStart.LAZY) { request() }.also { created ->
                requests[key] = created
                created.invokeOnCompletion {
                    synchronized(requests) {
                        if (requests[key] === created) requests.remove(key)
                    }
                }
            }
        }
        return task.await()
    }
}
