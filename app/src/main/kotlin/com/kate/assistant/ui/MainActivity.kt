package com.kate.assistant.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.kate.assistant.core.permissions.PermissionManager
import com.kate.assistant.services.KateService
import com.kate.assistant.ui.screens.HomeScreen
import com.kate.assistant.ui.theme.KateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PermissionManager.requestAll(this)
        PermissionManager.requestExactAlarm(this)
        startForegroundService(Intent(this, KateService::class.java))
        setContent { KateTheme { HomeScreen() } }
    }
}
