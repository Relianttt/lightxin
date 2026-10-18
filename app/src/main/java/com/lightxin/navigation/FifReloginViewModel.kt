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

    /** AI课堂映射失效：连 FIF 会话一起重建。 */
    suspend fun prepareRelogin() = mutex.withLock {
        repository.resetFifSession()
        sessionManager.logout()
        homeBootstrap.resetAfterRelogin()
    }

    /**
     * 只有校内登录态失效（查寝/劳动教育等）：**不动** FIF 会话。
     *
     * 查寝的“非法访问”是校内 token 或身份字段的问题，把 FIF 一起清掉等于顺手毁掉
     * 一个没坏的会话，用户还得重进一次 AI课堂。
     */
    suspend fun prepareSessionRelogin() = mutex.withLock {
        sessionManager.logout()
        homeBootstrap.resetAfterRelogin()
    }

    /**
     * 登录成功统一入口（普通登录与 FIF 重登共用）。
     *
     * 必须重新跑一次首页 bootstrap：冷启动时 MainActivity 已经调过一次 load()，那时未登录，
     * 它不会产出 snapshot；不重跑，首页就永远停在“正在加载…”。
     */
    suspend fun onLoginSuccess() = mutex.withLock {
        homeBootstrap.resetAfterRelogin()
        homeBootstrap.load()
    }
}
