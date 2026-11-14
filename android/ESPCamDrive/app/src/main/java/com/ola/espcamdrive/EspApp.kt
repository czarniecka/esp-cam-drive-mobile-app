package com.ola.espcamdrive

import android.app.Application

class EspApp : Application() {
    lateinit var model: AppModel
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        model = AppModel(this)
    }

    companion object {
        lateinit var instance: EspApp
            private set
    }
}
