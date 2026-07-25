package com.dti.kate.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dti.kate.core.VoskManager
import com.dti.kate.repository.Repository
import com.dti.kate.service.KateForegroundService
import com.dti.kate.ui.admin.AdminDashboardScreen
import com.dti.kate.ui.screen.*
import com.dti.kate.ui.theme.KateTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KateTheme {
                KateNavHost()
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun KateNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val voskManager = remember { VoskManager(context) }
    val repository = remember { Repository(context.applicationContext) }

    val isAuthenticated = repository.isAuthenticated()
    val startDestination = if (isAuthenticated) "home" else "splash"

    if (isAuthenticated) {
        val serviceIntent = Intent(context, KateForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("splash") { SplashScreen(navController) }
        composable("onboarding") { OnboardingScreen(navController) }
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("forgot_password") { ForgotPasswordScreen(navController) }
        composable("home") { HomeScreen(navController, voskManager = voskManager) }
        composable("history") { HistoryScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
        composable("premium") { PremiumScreen(navController) }
        composable("payment_result") { PaymentResultScreen(navController) }
        composable("admin_dashboard") { AdminDashboardScreen(navController) }
        composable("privacy_policy") { PrivacyPolicyScreen(navController) }
        composable("terms_of_service") { TermsOfServiceScreen(navController) }
    }
}
