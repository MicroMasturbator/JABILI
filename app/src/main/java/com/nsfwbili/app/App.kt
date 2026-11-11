package com.nsfwbili.app

import android.app.Application
import com.nsfwbili.app.data.LocalStore
import com.nsfwbili.app.data.PageLoader

class App : Application() {
    companion object {
        fun applyTheme() = androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            when (LocalStore.themeMode) {
                1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        PageLoader.init(this)
        LocalStore.init(this)
        com.nsfwbili.app.site.SiteRegistry.reload()
        applyTheme()
        // 动态取色：开关打开且系统支持时生效（新打开的页面起作用）
        com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(
            this,
            com.google.android.material.color.DynamicColorsOptions.Builder()
                .setPrecondition { _, _ -> LocalStore.dynamicColor }.build()
        )
    }
}
