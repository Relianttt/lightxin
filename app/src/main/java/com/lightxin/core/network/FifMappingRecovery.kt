package com.lightxin.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FifMappingRecoveryState(
    val failure: FifMappingFailure? = null,
    val isRetrying: Boolean = false,
)

/** Owned by a ViewModel: retain only failed operations, never replay successful writes. */
class FifMappingRecovery(private val resetSession: suspend () -> Unit) {
    private data class Pending(
        val failure: FifMappingFailure,
        val isCurrent: () -> Boolean,
        val retry: suspend () -> Unit,
    )

    private val pending = linkedMapOf<FifOperation, Pending>()
    private val inFlight = mutableMapOf<FifOperation, () -> Boolean>()
    private val mutableState = MutableStateFlow(FifMappingRecoveryState())
    val state = mutableState.asStateFlow()

    suspend fun <T> execute(
        operation: FifOperation,
        request: suspend () -> Result<T>,
        isCurrent: () -> Boolean = { true },
        onResult: (Result<T>) -> Unit,
    ) {
        if (inFlight[operation]?.invoke() == true) return
        inFlight[operation] = isCurrent
        try {
            val result: Result<T> = try { request() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            if (!isCurrent()) return
            val failure = result.exceptionOrNull()?.fifMappingFailure(operation)
            if (failure != null) {
                pending[operation] = Pending(failure, isCurrent) {
                    execute(operation, request, isCurrent, onResult)
                }
            } else {
                pending.remove(operation)
            }
            onResult(result)
            publish()
        } finally {
            if (inFlight[operation] === isCurrent) inFlight.remove(operation)
        }
    }

    suspend fun retry() {
        if (mutableState.value.isRetrying) return
        pending.entries.removeAll { !it.value.isCurrent() }
        val task = pending.values.firstOrNull() ?: return publish()
        mutableState.value = mutableState.value.copy(isRetrying = true)
        try {
            resetSession()
            task.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A local reset failure is not evidence of a broken account association.
            val failure = task.failure.copy(reason = FifMappingReason.UNKNOWN, httpStatus = null)
            pending[failure.operation] = task.copy(failure = failure)
        } finally {
            mutableState.value = mutableState.value.copy(isRetrying = false)
            publish()
        }
    }

    private fun publish() {
        pending.entries.removeAll { !it.value.isCurrent() }
        mutableState.value = mutableState.value.copy(failure = pending.values.firstOrNull()?.failure)
    }
}
