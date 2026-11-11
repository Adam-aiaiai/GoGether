package com.gogether.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.gogether.app.core.ui.GogetherViewModel
import com.gogether.app.feature.auth.LoginScreen
import com.gogether.app.feature.checkin.CheckInScreen
import com.gogether.app.feature.coordination.EventManagementScreen
import com.gogether.app.feature.event.CreateEventScreen
import com.gogether.app.feature.event.EventDetailScreen
import com.gogether.app.feature.explore.ExploreScreen
import com.gogether.app.feature.profile.ProfileScreen
import com.gogether.app.feature.registration.MyEventsScreen

private data class Destination(val route: String, val label: String, val glyph: String)
private val bottomDestinations = listOf(Destination("explore", "Explore", "⌕"), Destination("create", "Create", "+"), Destination("my_events", "My Events", "✓"), Destination("profile", "Profile", "●"))

@Composable
fun GogetherApp(viewModel: GogetherViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    if (currentUser == null) { LoginScreen(viewModel); return }
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(bottomBar = {
        if (bottomDestinations.any { it.route == route }) NavigationBar {
            bottomDestinations.forEach { destination -> NavigationBarItem(
                selected = route == destination.route,
                onClick = { navController.navigate(destination.route) { popUpTo("explore") { saveState = true }; launchSingleTop = true; restoreState = true } },
                icon = { Text(destination.glyph) }, label = { Text(destination.label) },
            ) }
        }
    }) { padding ->
        NavHost(navController, startDestination = "explore", modifier = Modifier.padding(padding)) {
            composable("explore") { ExploreScreen(viewModel) { navController.navigate("event/$it") } }
            composable("create") { CreateEventScreen(viewModel) { navController.navigate("event/$it") } }
            composable("my_events") { MyEventsScreen(viewModel, { navController.navigate("event/$it") }, { navController.navigate("manage/$it") }) }
            composable("profile") { ProfileScreen(viewModel) { navController.navigate("event/$it") } }
            composable("event/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { backStack ->
                EventDetailScreen(viewModel, backStack.arguments?.getString("eventId").orEmpty(), navController::popBackStack, { navController.navigate("manage/$it") }, { navController.navigate("checkin/$it") })
            }
            composable("manage/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { backStack -> EventManagementScreen(viewModel, backStack.arguments?.getString("eventId").orEmpty(), navController::popBackStack) }
            composable("checkin/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { backStack -> CheckInScreen(viewModel, backStack.arguments?.getString("eventId").orEmpty(), navController::popBackStack) }
        }
    }
}
