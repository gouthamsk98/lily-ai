package com.lilyai.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lilyai.app.ui.screens.addexpense.AddExpenseScreen
import com.lilyai.app.ui.screens.analytics.AnalyticsScreen
import com.lilyai.app.ui.screens.dashboard.DashboardScreen
import com.lilyai.app.ui.screens.history.HistoryScreen
import com.lilyai.app.ui.screens.login.LoginScreen
import com.lilyai.app.ui.screens.meetings.MeetingNotesScreen

sealed class Screen(val route: String, val label: String) {
    data object Login : Screen("login", "Login")
    data object Dashboard : Screen("dashboard", "Dashboard")
    data object AddExpense : Screen("add_expense", "Add")
    data object History : Screen("history", "History")
    data object Meetings : Screen("meetings", "Meetings")
    data object Analytics : Screen("analytics", "Analytics")
}

private val bottomNavItems = listOf(
    Screen.Dashboard, Screen.AddExpense, Screen.History, Screen.Meetings, Screen.Analytics
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != Screen.Login.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    when (screen) {
                                        Screen.Dashboard -> Icons.Default.Home
                                        Screen.AddExpense -> Icons.Default.Add
                                        Screen.History -> Icons.Default.List
                                        Screen.Meetings -> Icons.Default.Mic
                                        Screen.Analytics -> Icons.Default.Info
                                        else -> Icons.Default.Home
                                    },
                                    contentDescription = screen.label,
                                )
                            },
                            label = { Text(screen.label) },
                            selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Screen.Login.route) {
                LoginScreen(onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(onAddExpense = { navController.navigate(Screen.AddExpense.route) })
            }
            composable(Screen.AddExpense.route) {
                AddExpenseScreen(onExpenseAdded = { navController.popBackStack() })
            }
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Meetings.route) { MeetingNotesScreen() }
            composable(Screen.Analytics.route) { AnalyticsScreen() }
        }
    }
}
