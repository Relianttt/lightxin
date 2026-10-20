package com.lightxin.core.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

class AccountLease internal constructor(val generation: Long) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

data class AccountContext(val userCode: String, val lease: AccountLease)

/** Request owners outlive an individual screen, but never a login generation. */
@Singleton
class AccountSession @Inject constructor() {
    private val lock = Any()
    private val mutableLease = MutableStateFlow(AccountLease(0))
    val leases = mutableLease.asStateFlow()

    fun capture(): AccountLease = mutableLease.value
    fun isCurrent(lease: AccountLease): Boolean = mutableLease.value === lease

    fun requireCurrent(lease: AccountLease) {
        if (!isCurrent(lease)) throw CancellationException("Account session changed")
    }

    fun publish(lease: AccountLease, action: () -> Unit): Boolean = synchronized(lock) {
        if (!isCurrent(lease)) false else { action(); true }
    }

    fun invalidate() = synchronized(lock) {
        val old = capture()
        mutableLease.value = AccountLease(old.generation + 1)
        old.scope.cancel()
    }
}
