package com.dti.kate.core

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
