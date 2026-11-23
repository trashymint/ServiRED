package com.example.servired.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.servired.ui.screens.CreateReportScreen
import com.example.servired.ui.screens.LoginScreen
import com.example.servired.ui.screens.MyReportsScreen
import com.example.servired.ui.screens.RegisterScreen
import com.example.servired.ui.screens.ReportsMapScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(navController = navController)
        }

        composable("register") {
            RegisterScreen(navController = navController)
        }

        composable("my_reports") {
            MyReportsScreen(navController = navController)
        }

        composable("reports_map") {
            ReportsMapScreen(navController = navController)
        }

        composable("create_report") {
            CreateReportScreen(navController = navController)
        }
    }
}
