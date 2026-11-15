package com.hack.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hack.FreshCartApplication
import com.hack.data.session.SessionManager
import com.hack.ui.screens.admin.AdminDashboardScreen
import com.hack.ui.screens.auth.LoginScreen
import com.hack.ui.screens.customer.CustomerMainScreen
import com.hack.ui.theme.FreshCartTheme
import com.hack.ui.viewmodel.AdminViewModel
import com.hack.ui.viewmodel.AuthViewModel
import com.hack.ui.viewmodel.CustomerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreshCartTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FreshCartAppNavigation()
                }
            }
        }
    }
}

@Composable
fun FreshCartAppNavigation() {
    val navController = rememberNavController()
    val sessionManager = FreshCartApplication.instance.sessionManager

    val startDestination = when (sessionManager.currentRole) {
        SessionManager.ROLE_CUSTOMER -> "customer_main"
        SessionManager.ROLE_ADMIN -> "admin_main"
        else -> "login"
    }

    val authViewModel: AuthViewModel = viewModel()
    val customerViewModel: CustomerViewModel = viewModel()
    val adminViewModel: AdminViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateCustomer = {
                    navController.navigate("customer_main") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateAdmin = {
                    navController.navigate("admin_main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("customer_main") {
            CustomerMainScreen(
                viewModel = customerViewModel,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("login") {
                        popUpTo("customer_main") { inclusive = true }
                    }
                }
            )
        }

        composable("admin_main") {
            AdminDashboardScreen(
                viewModel = adminViewModel,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("login") {
                        popUpTo("admin_main") { inclusive = true }
                    }
                }
            )
        }
    }
}
