package com.lightxin

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.lightxin.core.auth.SessionManager
import com.lightxin.core.designsystem.theme.LightXinTheme
import com.lightxin.feature.home.domain.HomeBootstrap
import com.lightxin.feature.update.data.UpdateRepository
import com.lightxin.navigation.LightXinNavHost
import com.lightxin.navigation.ShortcutRouter
import com.lightxin.navigation.ShortcutTarget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var homeBootstrap: HomeBootstrap
    @Inject lateinit var shortcutRouter: ShortcutRouter
    @Inject lateinit var updateRepository: UpdateRepository

    private var shortcutTarget by mutableStateOf<ShortcutTarget?>(null)
    private var pendingDormTaskId by mutableStateOf<String?>(null)
    private var dormShortcutResolved by mutableStateOf(false)
    private var pendingNotificationRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // splash 放行闭包：数据就绪 OR 1500ms 超时均会置 false；两路解耦，超时不会取消加载协程
        var keepSplashOnScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)
        shortcutTarget = ShortcutTarget.fromAction(intent?.action)
        pendingNotificationRoute = intent?.getStringExtra("notification_route")

        // 独立协程：真实执行数据加载（即使超时也会继续跑完，结果写入 HomeBootstrap.snapshot）
        lifecycleScope.launch { homeBootstrap.load() }
        lifecycleScope.launch { updateRepository.checkForUpdate(force = false) }
        val dormShortcutReady = if (shortcutTarget == ShortcutTarget.DORM_CHECKIN) {
            lifecycleScope.async {
                pendingDormTaskId = shortcutRouter.resolveFirstUnsignedTask()
                dormShortcutResolved = true
            }
        } else {
            dormShortcutResolved = true
            null
        }
        // 独立协程：等待 ready 或超时，决定何时放行 splash
        lifecycleScope.launch {
            withTimeoutOrNull(SPLASH_MAX_WAIT_MS) {
                homeBootstrap.ready.first { it }
                dormShortcutReady?.await()
            }
            keepSplashOnScreen = false
        }

        // 状态栏与导航栏按系统深色模式切换图标明暗：浅色主题下用 light（深色图标贴暖底），深色主题下用 dark（浅色图标）
        val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val statusBarStyle = if (isNight) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        val navigationBarStyle = if (isNight) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle,
        )
        setContent {
            LightXinTheme {
                LightXinNavHost(
                    sessionManager = sessionManager,
                    shortcutTarget = shortcutTarget,
                    pendingDormTaskId = pendingDormTaskId,
                    isDormShortcutResolved = dormShortcutResolved,
                    pendingNotificationRoute = pendingNotificationRoute,
                    onShortcutConsumed = {
                        shortcutTarget = null
                        pendingDormTaskId = null
                        dormShortcutResolved = false
                        pendingNotificationRoute = null
                        shortcutRouter.consume()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 通知点击路由
        val notificationRoute = intent.getStringExtra("notification_route")
        if (notificationRoute != null) {
            pendingNotificationRoute = notificationRoute
            return
        }
        shortcutTarget = ShortcutTarget.fromAction(intent.action)
        pendingDormTaskId = null
        dormShortcutResolved = shortcutTarget != ShortcutTarget.DORM_CHECKIN
        shortcutRouter.consume()
        if (shortcutTarget == ShortcutTarget.DORM_CHECKIN) {
            lifecycleScope.launch {
                pendingDormTaskId = shortcutRouter.resolveFirstUnsignedTask()
                dormShortcutResolved = true
            }
        }
    }

    companion object {
        private const val SPLASH_MAX_WAIT_MS = 1500L
    }
}
