package com.dti.kate.ui

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

    NavHost(navController = navController, startDestination = "splash") {
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
    }
}
