package com.jpcottin.skylens

import android.app.Application
import android.content.Context
import com.jpcottin.skylens.domain.AppContainer

class SkyLensApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SkyLensApplication).container
