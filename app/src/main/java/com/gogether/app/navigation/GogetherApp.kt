package com.gogether.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.gogether.app.core.data.GogetherRepository
import com.gogether.app.core.model.AppSnapshot
import com.gogether.app.core.ui.*
import com.gogether.app.feature.auth.*
import com.gogether.app.feature.checkin.*
import com.gogether.app.feature.coordination.*
import com.gogether.app.feature.event.*
import com.gogether.app.feature.explore.*
import com.gogether.app.feature.profile.ProfileScreen
import com.gogether.app.feature.registration.*
import kotlinx.coroutines.launch

private data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("explore", "Explore", Icons.Default.Home),
    Destination("create", "Create", Icons.Default.Add),
    Destination("my-events", "My Events", Icons.Default.Favorite),
    Destination("profile", "Profile", Icons.Default.Person),
)

@Composable
fun GogetherApp(repository: GogetherRepository) {
    val state by repository.state.collectAsStateWithLifecycle()
    val auth: AuthViewModel = viewModel(factory = RepositoryViewModel.factory { AuthViewModel(repository) })
    val busy by auth.busy.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(auth) { auth.messages.collect { snackbars.showSnackbar(it.text) } }
    if (state.currentUser == null) {
        Scaffold(snackbarHost = { AppSnackbarHost(snackbars) }) { padding ->
            Box(Modifier.padding(padding)) { AuthScreen(state, busy, auth::login, auth::register) }
        }
    } else {
        // Changing profiles removes all old navigation/form state; only repository data is shared.
        key(state.currentUserId) { SignedInApp(repository, state, auth, snackbars) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInApp(repository: GogetherRepository, state: AppSnapshot, auth: AuthViewModel, snackbars: SnackbarHostState) {
    val nav = rememberNavController()
    val explore: ExploreViewModel = viewModel(factory = RepositoryViewModel.factory { ExploreViewModel(repository) })
    val eventsVm: EventViewModel = viewModel(factory = RepositoryViewModel.factory { EventViewModel(repository) })
    val registration: RegistrationViewModel = viewModel(factory = RepositoryViewModel.factory { RegistrationViewModel(repository) })
    val coordination: CoordinationViewModel = viewModel(factory = RepositoryViewModel.factory { CoordinationViewModel(repository) })
    val checkIn: CheckInViewModel = viewModel(factory = RepositoryViewModel.factory { CheckInViewModel(repository) })
    val now by explore.now.collectAsStateWithLifecycle()
    val events by explore.events.collectAsStateWithLifecycle()
    val filter by explore.filter.collectAsStateWithLifecycle()
    val eventBusy by eventsVm.busy.collectAsStateWithLifecycle()
    val registrationBusy by registration.busy.collectAsStateWithLifecycle()
    val coordinationBusy by coordination.busy.collectAsStateWithLifecycle()
    val checkInBusy by checkIn.busy.collectAsStateWithLifecycle()
    val authBusy by auth.busy.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "explore"
    val subpage = route.contains("{eventId}")
    val scope = rememberCoroutineScope()
    listOf(eventsVm, registration, coordination, checkIn).forEach { vm ->
        LaunchedEffect(vm) {
            vm.messages.collect { message ->
                message.eventId?.let { id ->
                    explore.filter(ExploreFilter())
                    nav.navigate("event/$id") { popUpTo("explore") }
                }
                scope.launch { snackbars.showSnackbar(message.text) }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (subpage) when {
                route.startsWith("manage") -> "Event management"
                route.startsWith("edit") -> "Edit event"
                route.startsWith("reuse") -> "Reuse event"
                route.startsWith("check-in") -> "Check-in & feedback"
                else -> "Event details"
            } else "Gogether") },
                navigationIcon = { if (subpage) IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                } }, actions = { Text("DEMO", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 16.dp)) })
        },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(selected = route == destination.route,
                        onClick = {
                            if (route != destination.route) {
                                if (destination.route == "explore") {
                                    nav.popBackStack("explore", inclusive = false)
                                } else nav.navigate(destination.route) {
                                    popUpTo("explore") { saveState = !subpage }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }, icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label, maxLines = 2, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }, modifier = Modifier.testTag("nav_${destination.route}"))
                }
            }
        }, snackbarHost = { AppSnackbarHost(snackbars) },
    ) { padding ->
        NavHost(navController = nav, startDestination = "explore", modifier = Modifier.padding(padding)) {
            composable("explore") { ExploreScreen(state, events, filter, now, explore::filter) { nav.navigate("event/$it") } }
            composable("create") { CreateEventScreen(eventBusy, eventsVm::create) }
            composable("my-events") { MyEventsScreen(state, now) { nav.navigate("event/$it") } }
            composable("profile") { ProfileScreen(state, now, authBusy, auth::login, auth::logout, coordination::read) { nav.navigate("event/$it") } }
            composable("event/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { backStack ->
                val id = backStack.arguments?.getString("eventId").orEmpty()
                val event = state.event(id)
                if (event == null) Page { InfoCard("Event not found", "Return to Explore to find another plan.") }
                else EventDetailScreen(event, state, now, registrationBusy,
                    { registration.join(id) }, { registration.cancel(id) },
                    { nav.navigate("manage/$id") }, { nav.navigate("check-in/$id") })
            }
            composable("manage/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { backStack ->
                val id = backStack.arguments?.getString("eventId").orEmpty()
                val event = state.event(id)
                if (event == null) Page { InfoCard("Event not found", "Return to Explore.") }
                else ManagementScreen(event, state, now, coordinationBusy,
                    { nav.navigate("edit/$id") }, { nav.navigate("reuse/$id") }, { coordination.cancel(id) },
                    { coordination.openCheckIn(id, it) }, { coordination.announce(id, it) })
            }
            composable("edit/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { backStack ->
                val id = backStack.arguments?.getString("eventId").orEmpty()
                val event = state.event(id)
                when {
                    event == null -> Page { InfoCard("Event not found", "Return to your event list.") }
                    event.organizerId != state.currentUserId -> Page { InfoCard("Organizer access", "Only this event's organizer can edit it.") }
                    else -> EditEventScreen(event, eventBusy) { eventsVm.update(id, it) }
                }
            }
            composable("reuse/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { backStack ->
                val id = backStack.arguments?.getString("eventId").orEmpty()
                val event = state.event(id)
                when {
                    event == null -> Page { InfoCard("Event not found", "Return to your event list.") }
                    event.organizerId != state.currentUserId -> Page { InfoCard("Organizer access", "Only this event's organizer can reuse it.") }
                    else -> ReuseEventScreen(event, eventBusy, eventsVm::create)
                }
            }
            composable("check-in/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { backStack ->
                val id = backStack.arguments?.getString("eventId").orEmpty()
                val event = state.event(id)
                if (event == null) Page { InfoCard("Event not found", "Return to Explore.") }
                else CheckInScreen(event, state, now, checkInBusy, { checkIn.checkIn(id) }, { rating, comment -> checkIn.feedback(id, rating, comment) })
            }
        }
    }
}

@Composable
private fun AppSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data -> Snackbar(data, Modifier.testTag("snackbar")) }
}
