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
import com.example.openruncoach.ui.goal.GoalScreen
import com.example.openruncoach.ui.home.HomeScreen
import com.example.openruncoach.ui.plan.PlanScreen
import com.example.openruncoach.ui.profile.PerformanceProfileScreen
import com.example.openruncoach.ui.settings.SettingsScreen
import com.example.openruncoach.ui.simulation.SimulationScreen
import com.example.openruncoach.ui.inspection.HealthDataInspectorScreen
import com.example.openruncoach.ui.workout.WorkoutDetailScreen
import com.example.openruncoach.ui.workout.WorkoutPreviewScreen

private object Routes {
  const val HOME = "home"
  const val HISTORY = "history"
  const val PLAN = "plan"
  const val SETTINGS = "settings"
  const val RUN_DETAIL = "run"
  const val COACH_DETAIL = "coach"
  const val SIMULATOR = "simulator"
  const val DATA_INSPECTOR = "data-inspector"
  const val PERFORMANCE_PROFILE = "performance-profile"
  const val GOAL = "goal"
  const val WORKOUT_PREVIEW = "workout-preview"
  const val WORKOUT_DETAIL = "planned-workout"
  const val RUN_ID = "runId"
  const val WORKOUT_ID = "workoutId"
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
          onViewPlan = { navController.navigate(Routes.PLAN) },
        )
      }
      composable(Routes.HISTORY) {
        HistoryScreen(onRunClick = { runId -> navController.navigate(runDetailRoute(runId)) })
      }
      composable(Routes.PLAN) {
        PlanScreen(
          onOpenGoal = { navController.navigate(Routes.GOAL) },
          onOpenWorkout = { workoutId -> navController.navigate(workoutDetailRoute(workoutId)) },
        )
      }
      composable(Routes.SETTINGS) {
        SettingsScreen(
          onOpenPerformanceProfile = { navController.navigate(Routes.PERFORMANCE_PROFILE) },
          onOpenTrainingGoal = { navController.navigate(Routes.GOAL) },
          onOpenSimulator = { navController.navigate(Routes.SIMULATOR) },
          onOpenWorkoutPreview = { navController.navigate(Routes.WORKOUT_PREVIEW) },
        )
      }
      composable(Routes.PERFORMANCE_PROFILE) { PerformanceProfileScreen(onBack = navController::navigateUp) }
      composable(Routes.GOAL) {
        GoalScreen(
          onBack = navController::navigateUp,
          onOpenTrainingPlan = { navController.navigate(Routes.PLAN) },
        )
      }
      composable(Routes.SIMULATOR) { SimulationScreen(onBack = navController::navigateUp) }
      composable(Routes.WORKOUT_PREVIEW) {
        WorkoutPreviewScreen(
          onBack = navController::navigateUp,
          onOpenWorkout = { workoutId -> navController.navigate(workoutDetailRoute(workoutId)) },
        )
      }
      composable(
        route = "${Routes.WORKOUT_DETAIL}?${Routes.WORKOUT_ID}={${Routes.WORKOUT_ID}}",
        arguments = listOf(navArgument(Routes.WORKOUT_ID) { type = NavType.StringType }),
      ) { entry ->
        WorkoutDetailScreen(
          workoutId = entry.arguments?.getString(Routes.WORKOUT_ID).orEmpty(),
          onBack = navController::navigateUp,
        )
      }
      composable(
        route = "${Routes.RUN_DETAIL}?${Routes.RUN_ID}={${Routes.RUN_ID}}",
        arguments = listOf(navArgument(Routes.RUN_ID) { type = NavType.StringType }),
      ) { entry ->
        val runId = entry.arguments?.getString(Routes.RUN_ID).orEmpty()
        RunDetailScreen(
          runId = runId,
          onBack = navController::navigateUp,
          onViewAnalysis = { selectedRunId -> navController.navigate(coachDetailRoute(selectedRunId)) },
          onInspectHealthData = { selectedRunId -> navController.navigate(dataInspectorRoute(selectedRunId)) },
        )
      }
      composable(
        route = "${Routes.DATA_INSPECTOR}?${Routes.RUN_ID}={${Routes.RUN_ID}}",
        arguments = listOf(navArgument(Routes.RUN_ID) { type = NavType.StringType }),
      ) { entry ->
        HealthDataInspectorScreen(
          runId = entry.arguments?.getString(Routes.RUN_ID).orEmpty(),
          onBack = navController::navigateUp,
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

private fun dataInspectorRoute(runId: String) = "${Routes.DATA_INSPECTOR}?${Routes.RUN_ID}=${Uri.encode(runId)}"

private fun workoutDetailRoute(workoutId: String) = "${Routes.WORKOUT_DETAIL}?${Routes.WORKOUT_ID}=${Uri.encode(workoutId)}"
