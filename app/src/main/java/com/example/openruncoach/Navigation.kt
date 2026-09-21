package com.example.openruncoach

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.openruncoach.ui.coach.CoachDetailScreen
import com.example.openruncoach.ui.health.RunDetailScreen
import com.example.openruncoach.ui.history.HistoryScreen
import com.example.openruncoach.ui.home.HomeScreen
import com.example.openruncoach.ui.plan.PlanScreen
import com.example.openruncoach.ui.settings.SettingsScreen

private object Routes {
  const val HOME = "home"
  const val HISTORY = "history"
  const val PLAN = "plan"
  const val SETTINGS = "settings"
  const val RUN_DETAIL = "run"
  const val COACH_DETAIL = "coach"
  const val RUN_ID = "runId"
}

private data class BottomDestination(val route: String, val label: String, val symbol: String)

private val bottomDestinations = listOf(
  BottomDestination(Routes.HOME, "Home", "⌂"),
  BottomDestination(Routes.HISTORY, "History", "≡"),
  BottomDestination(Routes.PLAN, "Plan", "□"),
  BottomDestination(Routes.SETTINGS, "Settings", "⚙"),
)

@Composable
fun MainNavigation() {
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = backStackEntry?.destination
  val showBottomBar = bottomDestinations.any { destination ->
    currentDestination?.hierarchy?.any { it.route == destination.route } == true
  }

  Scaffold(
    bottomBar = {
      if (showBottomBar) {
        NavigationBar {
          bottomDestinations.forEach { destination ->
            NavigationBarItem(
              selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
              onClick = {
                navController.navigate(destination.route) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = { Text(destination.symbol) },
              label = { Text(destination.label) },
            )
          }
        }
      }
    },
  ) { innerPadding ->
    NavHost(
      navController = navController,
      startDestination = Routes.HOME,
      modifier = Modifier.padding(innerPadding),
    ) {
      composable(Routes.HOME) {
        HomeScreen(
          onRunClick = { runId -> navController.navigate(runDetailRoute(runId)) },
          onViewAnalysis = { runId -> navController.navigate(coachDetailRoute(runId)) },
        )
      }
      composable(Routes.HISTORY) {
        HistoryScreen(onRunClick = { runId -> navController.navigate(runDetailRoute(runId)) })
      }
      composable(Routes.PLAN) { PlanScreen() }
      composable(Routes.SETTINGS) { SettingsScreen() }
      composable(
        route = "${Routes.RUN_DETAIL}?${Routes.RUN_ID}={${Routes.RUN_ID}}",
        arguments = listOf(navArgument(Routes.RUN_ID) { type = NavType.StringType }),
      ) { entry ->
        val runId = entry.arguments?.getString(Routes.RUN_ID).orEmpty()
        RunDetailScreen(
          runId = runId,
          onBack = navController::navigateUp,
          onViewAnalysis = { selectedRunId -> navController.navigate(coachDetailRoute(selectedRunId)) },
        )
      }
      composable(
        route = "${Routes.COACH_DETAIL}?${Routes.RUN_ID}={${Routes.RUN_ID}}",
        arguments = listOf(navArgument(Routes.RUN_ID) { type = NavType.StringType }),
      ) { entry ->
        CoachDetailScreen(
          runId = entry.arguments?.getString(Routes.RUN_ID).orEmpty(),
          onBack = navController::navigateUp,
        )
      }
    }
  }
}

private fun runDetailRoute(runId: String) = "${Routes.RUN_DETAIL}?${Routes.RUN_ID}=${Uri.encode(runId)}"

private fun coachDetailRoute(runId: String) = "${Routes.COACH_DETAIL}?${Routes.RUN_ID}=${Uri.encode(runId)}"
