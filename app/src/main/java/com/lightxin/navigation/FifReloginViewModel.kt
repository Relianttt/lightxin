package com.lightxin.navigation

import androidx.lifecycle.ViewModel
import com.lightxin.core.auth.SessionManager
import com.lightxin.feature.aiclass.data.AiClassRepository
import com.lightxin.feature.home.domain.HomeBootstrap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class FifReloginViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val repository: AiClassRepository,
    private val homeBootstrap: HomeBootstrap,
) : ViewModel() {
    private val mutex = Mutex()
    private var reloadHome = false

    suspend fun prepareRelogin() = mutex.withLock {
        repository.resetFifSession()
        sessionManager.logout()
        homeBootstrap.resetAfterRelogin()
        reloadHome = true
    }

    suspend fun onLoginSuccess() {
        if (reloadHome) {
            reloadHome = false
            homeBootstrap.load()
        }
    }
}
