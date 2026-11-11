package com.example.openruncoach.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.openruncoach.R
import com.example.openruncoach.theme.*

/** Labels change; existing route string values remain stable. */
internal data class BottomDestination(val route: String, val label: String, val icon: Int)
internal val bottomDestinations = listOf(
  BottomDestination("home", "Today", R.drawable.ic_nav_home),
  BottomDestination("plan", "Plan", R.drawable.ic_nav_plan),
  BottomDestination("history", "Runs", R.drawable.ic_nav_history),
)

internal fun showsBottomBar(route: String?) = bottomDestinations.any { it.route == route }
internal fun routeHasHero(route: String?) = route == "home" || route == "plan" || route?.startsWith("run-checkout?") == true

@Composable
internal fun OpenRunBottomBar(selectedRoute: String?, onSelect: (String) -> Unit) {
  val p = LocalOpenRunPalette.current
  Surface(modifier = Modifier.testTag("bottom-navigation"), color = p.nav, tonalElevation = 0.dp, shadowElevation = 0.dp) {
    Column(Modifier.navigationBarsPadding()) {
      HorizontalDivider(color = p.line, thickness = 1.dp)
      Row(Modifier.fillMaxWidth().selectableGroup()) {
        bottomDestinations.forEach { item ->
          val selected = item.route == selectedRoute
          val color = if (selected) p.accentText else p.muted
          Column(Modifier.weight(1f).heightIn(min = 56.dp)
            .selectable(selected, role = Role.Tab, onClick = { onSelect(item.route) })
            .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
            Text(item.label, style = OpenRunType.meta.copy(fontSize = 12.sp, lineHeight = 16.sp,
              fontWeight = FontWeight.SemiBold), color = color)
          }
        }
      }
    }
  }
}

@Composable
fun OpenRunSettingsIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val p = LocalOpenRunPalette.current
  FilledIconButton(onClick, modifier.size(44.dp),
    colors = IconButtonDefaults.filledIconButtonColors(containerColor = p.onHero.copy(alpha = .18f), contentColor = p.onHero)) {
    Icon(painterResource(R.drawable.ic_nav_settings), contentDescription = "Settings", modifier = Modifier.size(20.dp))
  }
}

private tailrec fun Context.activity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.activity()
  else -> null
}

/** Pure window presentation; does not touch app state or navigation history. */
@Composable
internal fun NavigationSystemBars(heroScreen: Boolean) {
  val view = LocalView.current
  val lightPalette = LocalOpenRunPalette.current == LightOpenRunPalette
  DisposableEffect(view, heroScreen, lightPalette) {
    val window = view.context.activity()?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    val oldStatus = controller?.isAppearanceLightStatusBars
    val oldNavigation = controller?.isAppearanceLightNavigationBars
    controller?.isAppearanceLightStatusBars = lightPalette && !heroScreen
    controller?.isAppearanceLightNavigationBars = lightPalette
    onDispose {
      if (oldStatus != null) controller.isAppearanceLightStatusBars = oldStatus
      if (oldNavigation != null) controller.isAppearanceLightNavigationBars = oldNavigation
    }
  }
}
