package com.lightxin

import android.app.Application
import com.lightxin.navigation.ShortcutRegistrar
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LightXinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShortcutRegistrar.register(this)
    }
}
